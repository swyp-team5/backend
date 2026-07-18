package com.autoschedule.notification.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.MemberNotificationSetting;
import com.autoschedule.notification.dto.NotificationSettingResponse;
import com.autoschedule.notification.dto.NotificationSettingUpdateRequest;
import com.autoschedule.notification.repository.MemberNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 FCM 푸시 수신 설정 조회와 변경을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final MemberRepository memberRepository;
    private final MemberNotificationSettingRepository memberNotificationSettingRepository;

    /**
     * 현재 회원의 알림 수신 설정을 조회하고, 없으면 기본 설정을 생성한다.
     */
    @Transactional
    public NotificationSettingResponse getMySetting(Long memberId) {
        MemberNotificationSetting setting = getOrCreateSetting(findActiveMember(memberId));
        return NotificationSettingResponse.from(setting);
    }

    /**
     * 현재 회원의 FCM 푸시 수신 여부를 변경한다.
     */
    @Transactional
    public NotificationSettingResponse updateMySetting(
            Long memberId,
            NotificationSettingUpdateRequest request
    ) {
        MemberNotificationSetting setting = getOrCreateSetting(findActiveMember(memberId));
        setting.updateFcmPushEnabled(request.fcmPushEnabled());
        return NotificationSettingResponse.from(setting);
    }

    /**
     * 회원의 알림 수신 설정이 있으면 반환하고, 없으면 기본값으로 생성한다.
     */
    private MemberNotificationSetting getOrCreateSetting(Member member) {
        return memberNotificationSettingRepository.findByMember_Id(member.getId())
                .orElseGet(() -> memberNotificationSettingRepository.save(
                        MemberNotificationSetting.createDefault(member)
                ));
    }

    /**
     * 인증된 회원이 활성 회원인지 확인한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }
}
