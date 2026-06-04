package com.stock.briefing.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class KakaoMessageController {

    private final KakaoMessageService kakaoMessageService;

    @GetMapping("/kakao")
    public Mono<String> testKakao() {
        return kakaoMessageService.sendMessage("카카오톡 테스트 메시지입니다.");
    }
}
