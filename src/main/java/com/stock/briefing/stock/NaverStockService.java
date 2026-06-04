package com.stock.briefing.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverStockService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String KOSPI_URL = "https://finance.naver.com/sise/sise_index.naver?code=KOSPI";
    private static final String KOSDAQ_URL = "https://finance.naver.com/sise/sise_index.naver?code=KOSDAQ";
    private static final String STOCK_URL = "https://finance.naver.com/item/main.naver?code=";

    private final WebClient webClient;

    public Mono<List<StockData>> getMarketSummary() {
        Mono<StockData> kospi = fetchIndex("KOSPI", KOSPI_URL);
        Mono<StockData> kosdaq = fetchIndex("KOSDAQ", KOSDAQ_URL);
        Mono<StockData> samsung = fetchStock("삼성전자", "005930");
        Mono<StockData> skHynix = fetchStock("SK하이닉스", "000660");
        Mono<StockData> naver = fetchStock("NAVER", "035420");

        return Mono.zip(kospi, kosdaq, samsung, skHynix, naver)
                .map(t -> List.of(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5()));
    }

    private Mono<StockData> fetchIndex(String name, String url) {
        return fetchHtml(url)
                .map(html -> parseIndex(name, html))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch index {}: {}", name, e.getMessage());
                    return Mono.just(new StockData(name, "N/A", "N/A", "N/A"));
                });
    }

    private Mono<StockData> fetchStock(String name, String code) {
        return fetchHtml(STOCK_URL + code)
                .map(html -> parseStock(name, html))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch stock {}: {}", name, e.getMessage());
                    return Mono.just(new StockData(name, "N/A", "N/A", "N/A"));
                });
    }

    private Mono<String> fetchHtml(String url) {
        return webClient.get()
                .uri(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .retrieve()
                .bodyToMono(String.class);
    }

    // HTML 구조: <em id="now_value">8,639.41</em>
    //           <span id="change_value_and_rate"><span>162.08</span> -1.84%<span class="blind">상승</span></span>
    private StockData parseIndex(String name, String html) {
        Document doc = Jsoup.parse(html);

        String price = Optional.ofNullable(doc.selectFirst("#now_value"))
                .map(Element::text).orElse("N/A");

        Element flucEl = doc.selectFirst("#change_value_and_rate");
        String change = "N/A";
        String changeRate = "N/A";

        if (flucEl != null) {
            // 직접 자식 요소만 순회해 blind 클래스 없는 첫 번째 span의 텍스트 추출
            change = flucEl.children().stream()
                    .filter(e -> !e.hasClass("blind"))
                    .findFirst()
                    .map(Element::text)
                    .orElse("N/A");
            // ownText()로 " -1.84%" 같은 rate 부분 추출
            String own = flucEl.ownText().trim();
            Matcher m = Pattern.compile("[+\\-]?\\d+\\.\\d+%").matcher(own);
            if (m.find()) {
                changeRate = m.group();
            }
        }

        log.debug("Index {} - price:{}, change:{}, rate:{}", name, price, change, changeRate);
        return new StockData(name, price, change, changeRate);
    }

    // HTML 구조: <p class="no_today"><em class="no_down|no_up"><span class="blind">351,500</span>...</em></p>
    //           <p class="no_exday"><em><span class="ico down|up">하락|상승</span><span class="blind">9,000</span></em>
    //                               <em><span class="blind">2.50</span><span class="per">%</span></em></p>
    private StockData parseStock(String name, String html) {
        Document doc = Jsoup.parse(html);

        String price = Optional.ofNullable(doc.selectFirst(".no_today em span.blind"))
                .map(Element::text).orElse("N/A");

        Element icoEl = doc.selectFirst(".no_exday em .ico");
        String sign = "";
        if (icoEl != null) {
            sign = icoEl.hasClass("up") ? "+" : icoEl.hasClass("down") ? "-" : "";
        }

        Elements blindSpans = doc.select(".no_exday em span.blind");
        String change = blindSpans.size() > 0 ? sign + blindSpans.get(0).text() : "N/A";
        String changeRate = blindSpans.size() > 1 ? sign + blindSpans.get(1).text() + "%" : "N/A";

        log.debug("Stock {} - price:{}, change:{}, rate:{}", name, price, change, changeRate);
        return new StockData(name, price, change, changeRate);
    }
}
