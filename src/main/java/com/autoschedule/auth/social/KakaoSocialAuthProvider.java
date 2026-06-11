package com.autoschedule.auth.social;

import com.autoschedule.auth.config.SocialAuthProperties;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.SocialProvider;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Kakao access token으로 사용자 정보 API를 호출해 소셜 식별자를 검증한다.
 */
@Component
public class KakaoSocialAuthProvider implements SocialAuthProvider {

    private final WebClient webClient;
    private final SocialAuthProperties socialAuthProperties;

    /**
     * Kakao 사용자 정보 API 호출을 위한 WebClient와 설정값을 주입받는다.
     */
    public KakaoSocialAuthProvider(WebClient.Builder webClientBuilder, SocialAuthProperties socialAuthProperties) {
        this.webClient = webClientBuilder.build();
        this.socialAuthProperties = socialAuthProperties;
    }

    /**
     * Kakao 제공자 전략임을 반환한다.
     */
    @Override
    public SocialProvider supports() {
        return SocialProvider.KAKAO;
    }

    /**
     * Kakao 사용자 정보 API 응답에서 id와 이메일을 추출한다.
     */
    @Override
    @SuppressWarnings("unchecked")
    public SocialUserInfo authenticate(SocialAuthCommand command) {
        if (!StringUtils.hasText(command.accessToken())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Kakao accessToken은 필수입니다.");
        }
        String userInfoUri = socialAuthProperties.kakao() == null
                ? null
                : socialAuthProperties.kakao().userInfoUri();
        if (!StringUtils.hasText(userInfoUri)) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "Kakao 로그인 설정이 완료되지 않았습니다.");
        }

        try {
            Map<String, Object> response = webClient.get()
                    .uri(userInfoUri)
                    .headers(headers -> headers.setBearerAuth(command.accessToken()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || response.get("id") == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Kakao 인증 정보가 올바르지 않습니다.");
            }
            String email = null;
            Object kakaoAccount = response.get("kakao_account");
            if (kakaoAccount instanceof Map<?, ?> account) {
                Object emailValue = account.get("email");
                email = emailValue == null ? null : String.valueOf(emailValue);
            }
            return new SocialUserInfo(SocialProvider.KAKAO, String.valueOf(response.get("id")), email);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Kakao 인증 정보 검증에 실패했습니다.");
        }
    }
}
