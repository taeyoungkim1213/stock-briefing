package com.stock.briefing.briefing;

import com.stock.briefing.scheduler.StockBriefingScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class BriefingController {

    private final StockBriefingScheduler stockBriefingScheduler;

    @GetMapping("/briefing")
    public Mono<String> runBriefing() {
        return stockBriefingScheduler.runBriefing();
    }
}
