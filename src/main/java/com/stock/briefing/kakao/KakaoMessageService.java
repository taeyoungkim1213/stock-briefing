package com.stock.briefing.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoMessageService {

    private static final String SEND_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send";

    private final KakaoTokenService kakaoTokenService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public Mono<String> sendMessage(String text) {
        String truncated = text.length() > 1000 ? text.substring(0, 997) + "..." : text;
        return kakaoTokenService.getValidAccessToken()
                .flatMap(accessToken -> doSend(accessToken, truncated));
    }

    private Mono<String> doSend(String accessToken, String text) {
        String templateObject = buildTemplateObject(text);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("template_object", templateObject);

        return webClient.post()
                .uri(SEND_URL)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        res -> res.bodyToMono(String.class)
                                .doOnNext(errBody -> log.error("Kakao API error body: {}", errBody))
                                .flatMap(errBody -> Mono.error(new RuntimeException("Kakao API error: " + res.statusCode() + " - " + errBody))))
                .bodyToMono(String.class)
                .doOnSuccess(res -> log.info("Kakao message sent: {}", res));
    }

    private String buildTemplateObject(String text) {
        try {
            Map<String, Object> template = Map.of(
                    "object_type", "text",
                    "text", text,
                    "link", Map.of(
                            "web_url", "https://finance.naver.com",
                            "mobile_web_url", "https://finance.naver.com"
                    )
            );
            return objectMapper.writeValueAsString(template);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build template object", e);
        }
    }
}
