package com.autoschedule.auth.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redis 기반 refresh token 저장소가 기기별 세션과 TTL을 올바르게 관리하는지 검증한다.
 */
@Testcontainers
@SpringBootTest(classes = RedisRefreshTokenStoreIntegrationTest.TestConfig.class)
class RedisRefreshTokenStoreIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 테스트 Redis 컨테이너 접속 정보를 Spring Redis 설정으로 주입한다.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /**
     * 저장 시 memberId와 deviceId를 조합한 키에 tokenHash를 저장하고 TTL을 설정한다.
     */
    @Test
    void saveStoresTokenHashWithDeviceKeyAndTtl() {
        refreshTokenStore.save(new RefreshTokenSession(1L, "device-1", "hash-1", Duration.ofMinutes(30)));

        String key = "auth:refresh-token:1:device-1";
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("hash-1");
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(29 * 60L, 30 * 60L);
    }

    /**
     * 같은 회원의 같은 기기로 다시 저장하면 기존 refresh token hash를 새 값으로 교체한다.
     */
    @Test
    void saveOverwritesSameDeviceSession() {
        refreshTokenStore.save(new RefreshTokenSession(1L, "device-1", "old-hash", Duration.ofMinutes(30)));
        refreshTokenStore.save(new RefreshTokenSession(1L, "device-1", "new-hash", Duration.ofMinutes(30)));

        assertThat(refreshTokenStore.findTokenHash(1L, "device-1")).contains("new-hash");
    }

    /**
     * 같은 회원이라도 deviceId가 다르면 서로 다른 refresh token 세션으로 유지한다.
     */
    @Test
    void saveKeepsDifferentDeviceSessionsSeparately() {
        refreshTokenStore.save(new RefreshTokenSession(1L, "iphone", "iphone-hash", Duration.ofMinutes(30)));
        refreshTokenStore.save(new RefreshTokenSession(1L, "ipad", "ipad-hash", Duration.ofMinutes(30)));

        assertThat(refreshTokenStore.findTokenHash(1L, "iphone")).contains("iphone-hash");
        assertThat(refreshTokenStore.findTokenHash(1L, "ipad")).contains("ipad-hash");
    }

    /**
     * 세션 삭제 시 해당 회원과 기기의 refresh token만 제거한다.
     */
    @Test
    void deleteRemovesOnlyRequestedDeviceSession() {
        refreshTokenStore.save(new RefreshTokenSession(1L, "iphone", "iphone-hash", Duration.ofMinutes(30)));
        refreshTokenStore.save(new RefreshTokenSession(1L, "ipad", "ipad-hash", Duration.ofMinutes(30)));

        refreshTokenStore.delete(1L, "iphone");

        assertThat(refreshTokenStore.findTokenHash(1L, "iphone")).isEmpty();
        assertThat(refreshTokenStore.findTokenHash(1L, "ipad")).contains("ipad-hash");
    }

    /**
     * Redis 저장소 테스트에 필요한 최소 Spring 설정을 구성한다.
     */
    @TestConfiguration
    @EnableAutoConfiguration
    @Import(RedisRefreshTokenStore.class)
    static class TestConfig {
    }
}
