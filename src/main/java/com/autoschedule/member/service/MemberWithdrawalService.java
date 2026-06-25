package com.autoschedule.member.service;

import com.autoschedule.auth.refresh.RefreshTokenStore;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.service.FcmTokenService;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회원탈퇴 신청, 유예 기간 내 탈퇴 취소, 인증 세션 정리를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private static final Duration WITHDRAWAL_GRACE_PERIOD = Duration.ofDays(30);

    private final MemberRepository memberRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final FcmTokenService fcmTokenService;

    /**
     * 회원을 탈퇴 유예 상태로 전환하고 모든 refresh token과 FCM token을 정리한다.
     */
    @Transactional
    public void requestWithdrawal(Long memberId) {
        Member member = findMember(memberId);
        validateNotWithdrawn(member);

        member.requestWithdrawal(LocalDateTime.now());
        fcmTokenService.deactivateAll(member.getId());
        deleteRefreshTokensAfterCommit(member.getId());
    }

    /**
     * 30일 유예 기간 안의 탈퇴 신청을 취소하고 회원을 정상 상태로 복구한다.
     */
    @Transactional
    public void cancelWithdrawal(Long memberId) {
        Member member = findMember(memberId);
        validateNotWithdrawn(member);

        if (member.getStatus() == MemberStatus.ACTIVE) {
            return;
        }

        if (!member.isWithinWithdrawalGracePeriod(LocalDateTime.now(), WITHDRAWAL_GRACE_PERIOD)) {
            throw new ApiException(ErrorCode.CONFLICT, "탈퇴 취소 가능 기간이 지났습니다.");
        }

        member.cancelWithdrawal();
    }

    /**
     * 인증된 회원 ID로 회원을 조회한다.
     */
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다."));
    }

    /**
     * 영구 탈퇴 처리된 회원은 셀프 탈퇴 신청/취소 대상에서 제외한다.
     */
    private void validateNotWithdrawn(Member member) {
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 탈퇴 완료된 회원입니다.");
        }
    }

    /**
     * DB 탈퇴 상태 변경이 커밋된 뒤 Redis refresh token 세션을 제거한다.
     */
    private void deleteRefreshTokensAfterCommit(Long memberId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                /**
                 * 회원탈퇴 트랜잭션 커밋 성공 후 Redis 세션을 정리한다.
                 */
                @Override
                public void afterCommit() {
                    refreshTokenStore.deleteAll(memberId);
                }
            });
            return;
        }

        refreshTokenStore.deleteAll(memberId);
    }
}
