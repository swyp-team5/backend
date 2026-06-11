package com.autoschedule.auth.social;

import com.autoschedule.auth.config.SocialAuthProperties;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.SocialProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Google ID Token의 서명, aud, issuer, 만료 시간을 검증한다.
 */
@Component
@RequiredArgsConstructor
public class GoogleSocialAuthProvider implements SocialAuthProvider {

    private final SocialAuthProperties socialAuthProperties;

    /**
     * Google 제공자 전략임을 반환한다.
     */
    @Override
    public SocialProvider supports() {
        return SocialProvider.GOOGLE;
    }

    /**
     * Google ID Token을 검증하고 subject와 이메일을 추출한다.
     */
    @Override
    public SocialUserInfo authenticate(SocialAuthCommand command) {
        if (!StringUtils.hasText(command.idToken())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Google idToken은 필수입니다.");
        }
        if (socialAuthProperties.google() == null
                || CollectionUtils.isEmpty(socialAuthProperties.google().audiences())) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "Google 로그인 설정이 완료되지 않았습니다.");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(socialAuthProperties.google().audiences())
                    .build();
            GoogleIdToken idToken = verifier.verify(command.idToken());
            if (idToken == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Google 인증 정보가 올바르지 않습니다.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new SocialUserInfo(SocialProvider.GOOGLE, payload.getSubject(), payload.getEmail());
        } catch (GeneralSecurityException | IOException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Google 인증 정보 검증에 실패했습니다.");
        }
    }
}
