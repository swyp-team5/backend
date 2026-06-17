package com.autoschedule.notification.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.FcmTokenStatus;
import com.autoschedule.notification.domain.Notification;
import com.autoschedule.notification.domain.NotificationDelivery;
import com.autoschedule.notification.domain.PushPolicy;
import com.autoschedule.notification.dto.NotificationSendCommand;
import com.autoschedule.notification.event.NotificationPushRequestedEvent;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.repository.NotificationDeliveryRepository;
import com.autoschedule.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 기능에서 회원에게 보낼 알림과 FCM 발송 대기 이력을 생성한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final MemberRepository memberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 단일 회원에게 알림을 저장하고 PUSH 정책이면 커밋 이후 FCM 발송 이벤트를 발행한다.
     */
    @Transactional
    public Long sendToMember(Long receiverMemberId, NotificationSendCommand command) {
        Member receiver = findActiveMember(receiverMemberId);
        Notification notification = notificationRepository.save(Notification.create(
                receiver,
                command.notificationType(),
                command.pushPolicy(),
                command.title(),
                command.body(),
                toJson(command.data())
        ));

        if (command.pushPolicy() == PushPolicy.PUSH) {
            publishPushEventIfNeeded(createPendingDeliveries(notification));
        }
        return notification.getId();
    }

    /**
     * 회원의 활성 FCM 토큰마다 PENDING delivery를 생성한다.
     */
    private List<Long> createPendingDeliveries(Notification notification) {
        List<FcmToken> fcmTokens = fcmTokenRepository.findByMember_IdAndStatus(
                notification.getReceiverMember().getId(),
                FcmTokenStatus.ACTIVE
        );
        List<Long> deliveryIds = new ArrayList<>();
        for (FcmToken fcmToken : fcmTokens) {
            NotificationDelivery delivery = notificationDeliveryRepository.save(NotificationDelivery.createFcmPending(
                    notification,
                    fcmToken.getId(),
                    null
            ));
            deliveryIds.add(delivery.getId());
        }
        return deliveryIds;
    }

    /**
     * 생성된 delivery가 있을 때만 커밋 이후 FCM 발송 이벤트를 발행한다.
     */
    private void publishPushEventIfNeeded(List<Long> deliveryIds) {
        if (!deliveryIds.isEmpty()) {
            eventPublisher.publishEvent(new NotificationPushRequestedEvent(deliveryIds));
        }
    }

    /**
     * 인증 주체가 활성 회원인지 확인한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "알림 수신 회원을 찾을 수 없습니다."));
    }

    /**
     * 알림 부가 데이터를 JSON 문자열로 변환한다.
     */
    private String toJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "알림 부가 데이터 형식이 올바르지 않습니다.");
        }
    }
}
