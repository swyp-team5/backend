package com.autoschedule.crew.redis;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 크루 초대 코드의 TTL과 실패 횟수 제한을 Redis에 저장한다.
 */
@Component
@RequiredArgsConstructor
public class CrewInvitationRedisStore {

    private static final String INVITATION_KEY_PREFIX = "crew-invitation:";
    private static final String ATTEMPT_KEY_PREFIX = "crew-invitation-attempt:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 초대 코드와 초대 ID를 지정된 TTL 동안 Redis에 저장한다.
     */
    public void saveInvitation(String inviteCode, Long invitationId, Duration ttl) {
        redisTemplate.opsForValue().set(invitationKey(inviteCode), String.valueOf(invitationId), ttl);
    }

    /**
     * Redis에 남아 있는 초대 ID를 조회한다.
     */
    public Optional<Long> findInvitationId(String inviteCode) {
        String invitationId = redisTemplate.opsForValue().get(invitationKey(inviteCode));
        if (invitationId == null) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(invitationId));
    }

    /**
     * 초대 코드와 실패 횟수 키를 모두 제거한다.
     */
    public void deleteAll(String inviteCode) {
        redisTemplate.delete(invitationKey(inviteCode));
        redisTemplate.delete(attemptKey(inviteCode));
    }

    /**
     * 초대 코드 실패 횟수를 증가시키고 첫 실패 시 TTL을 설정한다.
     */
    public int incrementFailedAttempt(String inviteCode, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(attemptKey(inviteCode));
        if (count != null && count == 1L) {
            redisTemplate.expire(attemptKey(inviteCode), ttl);
        }
        return count == null ? 0 : count.intValue();
    }

    /**
     * 현재 Redis에 기록된 실패 횟수를 조회한다.
     */
    public int getFailedAttemptCount(String inviteCode) {
        String count = redisTemplate.opsForValue().get(attemptKey(inviteCode));
        if (count == null) {
            return 0;
        }
        return Integer.parseInt(count);
    }

    /**
     * 초대 코드 TTL을 조회한다.
     */
    public Duration getInvitationTtl(String inviteCode) {
        Long seconds = redisTemplate.getExpire(invitationKey(inviteCode));
        if (seconds == null || seconds < 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(seconds);
    }

    /**
     * 초대 코드 Redis 키를 생성한다.
     */
    private String invitationKey(String inviteCode) {
        return INVITATION_KEY_PREFIX + inviteCode;
    }

    /**
     * 초대 코드 실패 횟수 Redis 키를 생성한다.
     */
    private String attemptKey(String inviteCode) {
        return ATTEMPT_KEY_PREFIX + inviteCode;
    }
}
