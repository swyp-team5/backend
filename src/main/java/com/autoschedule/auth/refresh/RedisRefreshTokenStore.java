package com.autoschedule.auth.refresh;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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
     * 회원의 모든 기기 refresh token 세션을 Redis key 패턴으로 찾아 제거한다.
     */
    @Override
    public void deleteAll(Long memberId) {
        List<String> keys = new ArrayList<>();
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(KEY_PREFIX + ":" + memberId + ":*")
                .count(1000)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            cursor.forEachRemaining(keys::add);
        }

        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Redis key를 일관된 형식으로 생성한다.
     */
    private String key(Long memberId, String deviceId) {
        return KEY_PREFIX + ":" + memberId + ":" + deviceId;
    }
}
