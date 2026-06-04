package com.stock.briefing.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class StockController {

    private final NaverStockService naverStockService;

    @GetMapping("/stock")
    public Mono<List<StockData>> getMarketSummary() {
        return naverStockService.getMarketSummary();
    }
}
