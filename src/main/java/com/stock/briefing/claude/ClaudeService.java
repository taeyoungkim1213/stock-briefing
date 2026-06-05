package com.stock.briefing.claude;

import com.stock.briefing.stock.StockItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeService {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";

    private static final String PROMPT_TEMPLATE = """
            당신은 10년 경력의 전문 주식 애널리스트입니다.
            아래 시장 데이터와 뉴스를 바탕으로 오늘의 브리핑을 작성해주세요.
            투자 공부 중인 개인 투자자를 위해 핵심만 간결하게, 전문 용어는 쉽게 풀어서 작성해주세요.

            [국내 시장 데이터]
            {domesticData}

            [미국 시장 데이터]
            {usData}

            [오늘의 주요 뉴스]
            {newsData}

            [브리핑 형식]
            📊 오늘의 주식 브리핑 - {날짜}

            🇰🇷 국내 시장
            - 시장 분위기 (코스피/코스닥 흐름 2줄)
            - 반도체 섹터: 동향 1줄
            - 바이오 섹터: 동향 1줄
            - 2차전지 섹터: 동향 1줄

            🇺🇸 미국 시장
            - 지수 흐름 1줄
            - 반도체/바이오 동향 1줄

            📰 오늘의 핵심 뉴스
            - 주요 뉴스 2~3줄 요약

            ⚠️ 오늘의 리스크 포인트
            - 주의해야 할 점 1~2줄

            💡 공부 포인트
            - 오늘 시장 흐름에서 배울 수 있는 것 1~2줄
            """;

    private final WebClient webClient;
    private final AnthropicProperties anthropicProperties;

    public Mono<String> analyzeStock(List<StockItem> domestic, List<StockItem> us, List<String> news) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));
        String newsData = news.isEmpty() ? "뉴스 데이터 없음" :
                news.stream().map(n -> "- " + n).collect(Collectors.joining("\n"));

        String prompt = PROMPT_TEMPLATE
                .replace("{domesticData}", formatStockItems(domestic, true))
                .replace("{usData}", formatStockItems(us, false))
                .replace("{newsData}", newsData)
                .replace("{날짜}", date);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        return webClient.post()
                .uri(API_URL)
                .header("x-api-key", anthropicProperties.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        res -> res.bodyToMono(String.class)
                                .doOnNext(err -> log.error("Claude API 오류 응답: {}", err))
                                .flatMap(err -> Mono.error(new RuntimeException("Claude API error: " + err))))
                .bodyToMono(Map.class)
                .map(this::extractText)
                .doOnError(e -> log.error("Claude API 호출 실패: {}", e.getMessage()));
    }

    private String formatStockItems(List<StockItem> items, boolean isKr) {
        if (items.isEmpty()) return "데이터 없음";

        Map<String, List<StockItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(StockItem::getSector, LinkedHashMap::new, Collectors.toList()));

        StringBuilder sb = new StringBuilder();
        grouped.forEach((sector, stocks) -> {
            sb.append("[").append(sectorDisplayName(sector)).append("]\n");
            stocks.forEach(s -> {
                if (isKr) {
                    sb.append(String.format("%s: %s (%s, %s)%n",
                            s.getName(), s.getPrice(), s.getChange(), s.getChangeRate()));
                } else {
                    sb.append(String.format("%s(%s): %s (%s, %s)%n",
                            s.getName(), s.getSymbol(), s.getPrice(), s.getChange(), s.getChangeRate()));
                }
            });
            sb.append("\n");
        });
        return sb.toString().trim();
    }

    private String sectorDisplayName(String sector) {
        return switch (sector.toLowerCase()) {
            case "index" -> "지수";
            case "semiconductor" -> "반도체";
            case "bio" -> "바이오";
            case "battery" -> "2차전지";
            default -> sector;
        };
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }
}
