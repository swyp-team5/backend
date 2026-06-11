package com.autoschedule.auth.refresh;

import java.util.Optional;

/**
 * refresh token을 저장하고 조회하는 저장소 경계다.
 */
public interface RefreshTokenStore {

    /**
     * 회원과 기기 단위 refresh token hash를 저장한다.
     */
    void save(RefreshTokenSession session);

    /**
     * 회원과 기기 단위로 저장된 refresh token hash를 조회한다.
     */
    Optional<String> findTokenHash(Long memberId, String deviceId);

    /**
     * 회원과 기기 단위 refresh token 세션을 제거한다.
     */
    void delete(Long memberId, String deviceId);
}
