package com.autoschedule.auth.refresh;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis에 회원 기기별 refresh token hash를 저장한다.
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh-token";

    private final StringRedisTemplate redisTemplate;

    /**
     * `auth:refresh-token:{memberId}:{deviceId}` 키에 token hash를 TTL과 함께 저장한다.
     */
    @Override
    public void save(RefreshTokenSession session) {
        redisTemplate.opsForValue().set(
                key(session.memberId(), session.deviceId()),
                session.tokenHash(),
                session.ttl()
        );
    }

    /**
     * 회원과 기기 조합으로 저장된 token hash를 조회한다.
     */
    @Override
    public Optional<String> findTokenHash(Long memberId, String deviceId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(memberId, deviceId)));
    }

    /**
     * 회원과 기기 조합의 refresh token 세션을 삭제한다.
     */
    @Override
    public void delete(Long memberId, String deviceId) {
        redisTemplate.delete(key(memberId, deviceId));
    }

    /**
     * Redis key를 일관된 형식으로 생성한다.
     */
    private String key(Long memberId, String deviceId) {
        return KEY_PREFIX + ":" + memberId + ":" + deviceId;
    }
}
