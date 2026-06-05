package com.stock.briefing.scheduler;

import com.stock.briefing.claude.ClaudeService;
import com.stock.briefing.kakao.KakaoMessageService;
import com.stock.briefing.stock.NaverNewsService;
import com.stock.briefing.stock.PythonStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockBriefingScheduler {

    private final PythonStockService pythonStockService;
    private final NaverNewsService naverNewsService;
    private final ClaudeService claudeService;
    private final KakaoMessageService kakaoMessageService;

    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void sendDailyBriefing() {
        runBriefing()
                .subscribe(
                        result -> log.info("주식 브리핑 전송 완료"),
                        error -> log.error("주식 브리핑 전송 실패: {}", error.getMessage())
                );
    }

    public Mono<String> runBriefing() {
        var domestic = pythonStockService.getDomesticData()
                .onErrorResume(e -> {
                    log.error("국내 주식 데이터 수집 실패: {}", e.getMessage());
                    return Mono.just(List.of());
                });

        var us = pythonStockService.getUsData()
                .onErrorResume(e -> {
                    log.error("미국 주식 데이터 수집 실패: {}", e.getMessage());
                    return Mono.just(List.of());
                });

        var news = naverNewsService.getMarketNews()
                .onErrorResume(e -> {
                    log.error("뉴스 수집 실패: {}", e.getMessage());
                    return Mono.just(List.of());
                });

        return Mono.zip(domestic, us, news)
                .flatMap(t -> claudeService.analyzeStock(t.getT1(), t.getT2(), t.getT3()))
                .flatMap(kakaoMessageService::sendMessage);
    }
}
