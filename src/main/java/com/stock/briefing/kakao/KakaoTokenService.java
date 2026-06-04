package com.stock.briefing.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoTokenService {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

    private final KakaoProperties kakaoProperties;
    private final WebClient webClient;

    public Mono<String> getValidAccessToken() {
        return refreshAccessToken()
                .map(newToken -> {
                    log.info("Access token refreshed successfully");
                    return newToken;
                })
                .onErrorResume(e -> {
                    log.warn("Token refresh failed, using existing token: {}", e.getMessage());
                    return Mono.just(kakaoProperties.getAccessToken());
                });
    }

    private Mono<String> refreshAccessToken() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", kakaoProperties.getRestApiKey());
        params.add("refresh_token", kakaoProperties.getRefreshToken());
        params.add("client_secret", kakaoProperties.getClientSecret());

        return webClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> {
                    String newAccessToken = response.path("access_token").asText();
                    kakaoProperties.setAccessToken(newAccessToken);

                    if (response.hasNonNull("refresh_token")) {
                        kakaoProperties.setRefreshToken(response.path("refresh_token").asText());
                    }

                    saveTokensToYml(kakaoProperties.getAccessToken(), kakaoProperties.getRefreshToken());
                    return Mono.just(newAccessToken);
                });
    }

    private void saveTokensToYml(String accessToken, String refreshToken) {
        try {
            Path ymlPath = Paths.get("src/main/resources/application.yml");
            if (!Files.exists(ymlPath)) {
                // 런타임 환경(jar 실행)에서는 classpath 기준으로 찾기
                ymlPath = Paths.get(new ClassPathResource("application.yml").getURI());
            }

            String content = Files.readString(ymlPath, StandardCharsets.UTF_8);
            content = content.replaceAll("(?m)^(\\s*access-token:\\s*).*$", "$1" + accessToken);
            content = content.replaceAll("(?m)^(\\s*refresh-token:\\s*).*$", "$1" + refreshToken);
            Files.writeString(ymlPath, content, StandardCharsets.UTF_8);
            log.info("Tokens saved to application.yml");
        } catch (IOException e) {
            log.warn("Failed to save tokens to application.yml: {}", e.getMessage());
        }
    }
}
