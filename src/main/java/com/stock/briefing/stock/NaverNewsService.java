package com.stock.briefing.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverNewsService {

    private static final String NEWS_URL =
            "https://finance.naver.com/news/news_list.naver?mode=LSS2D&section_id=101&section_id2=258";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final WebClient webClient;

    public Mono<List<String>> getMarketNews() {
        return webClient.get()
                .uri(NEWS_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseNews)
                .onErrorResume(e -> {
                    log.warn("뉴스 크롤링 실패: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<String> parseNews(String html) {
        var doc = Jsoup.parse(html);
        Elements titles = doc.select("dl.newsList dt.articleSubject a");
        if (titles.isEmpty()) {
            titles = doc.select(".articleSubject a");
        }
        if (titles.isEmpty()) {
            titles = doc.select("ul.newsList li a");
        }
        return titles.stream()
                .map(Element::text)
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .limit(5)
                .collect(Collectors.toList());
    }
}
