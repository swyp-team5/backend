package com.autoschedule.notification.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.dto.FcmTokenRegisterRequest;
import com.autoschedule.notification.dto.FcmTokenResponse;
import com.autoschedule.notification.repository.FcmTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 기기별 FCM 토큰 등록, 갱신, 비활성화를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final MemberRepository memberRepository;
    private final FcmTokenRepository fcmTokenRepository;

    /**
     * 회원의 기기별 FCM 토큰을 등록하거나 최신 값으로 갱신한다.
     */
    @Transactional
    public FcmTokenResponse register(Long memberId, FcmTokenRegisterRequest request) {
        Member member = findActiveMember(memberId);
        LocalDateTime now = LocalDateTime.now();
        FcmToken fcmToken = fcmTokenRepository.findByMember_IdAndDeviceId(member.getId(), request.deviceId())
                .map(existingToken -> {
                    existingToken.updateToken(
                            request.token(),
                            request.platform(),
                            request.appVersion(),
                            now
                    );
                    return existingToken;
                })
                .orElseGet(() -> fcmTokenRepository.save(FcmToken.create(
                        member,
                        request.deviceId(),
                        request.token(),
                        request.platform(),
                        request.appVersion(),
                        now
                )));
        return FcmTokenResponse.from(fcmToken);
    }

    /**
     * 회원 본인의 특정 기기 FCM 토큰을 비활성화한다.
     */
    @Transactional
    public void deactivate(Long memberId, String deviceId) {
        fcmTokenRepository.findByMember_IdAndDeviceId(memberId, deviceId)
                .ifPresent(fcmToken -> fcmToken.deactivate(LocalDateTime.now()));
    }

    /**
     * 인증 주체가 활성 회원인지 확인한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }
}
