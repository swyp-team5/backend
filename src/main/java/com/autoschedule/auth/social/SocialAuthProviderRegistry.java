package com.autoschedule.auth.social;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.SocialProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 등록된 소셜 인증 전략 중 요청 provider에 맞는 전략을 찾아준다.
 */
@Component
public class SocialAuthProviderRegistry {

    private final Map<SocialProvider, SocialAuthProvider> providers;

    /**
     * Spring Bean으로 등록된 소셜 인증 전략들을 provider별로 인덱싱한다.
     */
    public SocialAuthProviderRegistry(List<SocialAuthProvider> providers) {
        this.providers = new EnumMap<>(SocialProvider.class);
        for (SocialAuthProvider provider : providers) {
            this.providers.put(provider.supports(), provider);
        }
    }

    /**
     * 요청 provider를 담당하는 소셜 인증 전략을 반환한다.
     */
    public SocialAuthProvider get(SocialProvider provider) {
        SocialAuthProvider socialAuthProvider = providers.get(provider);
        if (socialAuthProvider == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다.");
        }
        return socialAuthProvider;
    }
}
