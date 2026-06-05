package com.stock.briefing.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonStockService {

    private final ObjectMapper objectMapper;
    private final StockProperties stockProperties;

    @Value("${stock.python.executable:python}")
    private String pythonExecutable;

    @Value("${stock.python.script-dir:src/main/python}")
    private String scriptDir;

    @Value("${stock.python.timeout-seconds:30}")
    private int timeoutSeconds;

    public Mono<List<StockItem>> getDomesticData() {
        return Mono.fromCallable(() -> {
            String inputJson = objectMapper.writeValueAsString(buildDomesticInput());
            return runScript("fetch_domestic.py", inputJson);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.error("국내 주식 Python 스크립트 실패: {}", e.getMessage());
            return Mono.just(List.of());
        });
    }

    public Mono<List<StockItem>> getUsData() {
        return Mono.fromCallable(() -> {
            String inputJson = objectMapper.writeValueAsString(buildUsInput());
            return runScript("fetch_us.py", inputJson);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.error("미국 주식 Python 스크립트 실패: {}", e.getMessage());
            return Mono.just(List.of());
        });
    }

    private Map<String, Object> buildDomesticInput() {
        StockProperties.Domestic domestic = stockProperties.getDomestic();

        List<Map<String, String>> indices = domestic.getIndices().stream()
                .map(c -> Map.of("code", c.getCode(), "name", c.getName()))
                .collect(Collectors.toList());

        Map<String, List<Map<String, String>>> sectors = new LinkedHashMap<>();
        domestic.getSectors().forEach((sectorName, items) -> {
            List<Map<String, String>> sectorItems = items.stream()
                    .map(c -> Map.of("code", c.getCode(), "name", c.getName()))
                    .collect(Collectors.toList());
            sectors.put(sectorName, sectorItems);
        });

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("indices", indices);
        input.put("sectors", sectors);
        return input;
    }

    private Map<String, Object> buildUsInput() {
        StockProperties.Symbols symbols = stockProperties.getUs().getSymbols();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("indices", toSymbolList(symbols.getIndices()));
        input.put("semiconductor", toSymbolList(symbols.getSemiconductor()));
        input.put("bio", toSymbolList(symbols.getBio()));
        return input;
    }

    private List<Map<String, String>> toSymbolList(List<StockProperties.ItemConfig> items) {
        return items.stream()
                .map(c -> Map.of("symbol", c.getSymbol(), "name", c.getName()))
                .collect(Collectors.toList());
    }

    private List<StockItem> runScript(String scriptName, String inputJson) throws Exception {
        String scriptPath = resolveScriptPath(scriptName);
        log.debug("Python 스크립트 실행: {} {}", pythonExecutable, scriptPath);

        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptPath);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // stdin 쓰기는 별도 스레드 (데드락 방지)
        Thread stdinWriter = new Thread(() -> {
            try (var out = process.getOutputStream()) {
                out.write(inputJson.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                log.warn("Python stdin 쓰기 실패: {}", e.getMessage());
            }
        });
        stdinWriter.start();

        // stderr 읽기도 별도 스레드 (버퍼 블로킹 방지)
        StringBuilder stderrBuf = new StringBuilder();
        Thread stderrReader = new Thread(() -> {
            try (var r = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> stderrBuf.append(line).append("\n"));
            } catch (IOException ignored) {}
        });
        stderrReader.start();

        // stdout 메인 스레드에서 읽기
        String stdout;
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            stdout = reader.lines().collect(Collectors.joining("\n"));
        }

        stdinWriter.join(5_000);
        stderrReader.join(5_000);

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.error("Python 스크립트 타임아웃 ({}s): {}", timeoutSeconds, scriptName);
            return List.of();
        }

        int exitCode = process.exitValue();
        if (!stderrBuf.isEmpty()) {
            log.debug("Python stderr [{}]:\n{}", scriptName, stderrBuf.toString().trim());
        }
        if (exitCode != 0) {
            log.error("Python 스크립트 비정상 종료 (exitCode={}): {}", exitCode, scriptName);
            return List.of();
        }

        if (stdout.isBlank()) {
            log.warn("Python 스크립트 출력 없음: {}", scriptName);
            return List.of();
        }

        return objectMapper.readValue(stdout,
                objectMapper.getTypeFactory().constructCollectionType(List.class, StockItem.class));
    }

    private String resolveScriptPath(String scriptName) {
        Path path = Paths.get(scriptDir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(scriptDir);
        }
        return path.resolve(scriptName).toString();
    }
}
