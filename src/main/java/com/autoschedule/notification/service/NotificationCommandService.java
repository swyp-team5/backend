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
import com.autoschedule.notification.infra.FcmMessage;
import com.autoschedule.notification.infra.FcmSendResult;
import com.autoschedule.notification.infra.FcmSender;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.repository.NotificationDeliveryRepository;
import com.autoschedule.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 기능에서 회원에게 알림을 생성하고 FCM 발송을 시도할 때 사용하는 내부 서비스다.
 */
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final MemberRepository memberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final FcmSender fcmSender;
    private final ObjectMapper objectMapper;

    /**
     * 단일 회원에게 앱 내 알림을 저장하고, 정책에 따라 FCM 발송을 시도한다.
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
            sendFcmToActiveTokens(notification, command);
        }
        return notification.getId();
    }

    /**
     * 회원의 활성 FCM 토큰마다 발송을 시도하고 delivery 이력을 기록한다.
     */
    private void sendFcmToActiveTokens(Notification notification, NotificationSendCommand command) {
        List<FcmToken> fcmTokens = fcmTokenRepository.findByMember_IdAndStatus(
                notification.getReceiverMember().getId(),
                FcmTokenStatus.ACTIVE
        );
        for (FcmToken fcmToken : fcmTokens) {
            LocalDateTime attemptedAt = LocalDateTime.now();
            fcmToken.markUsed(attemptedAt);
            NotificationDelivery delivery = NotificationDelivery.createFcmAttempt(
                    notification,
                    fcmToken.getId(),
                    attemptedAt
            );
            FcmSendResult sendResult = fcmSender.send(new FcmMessage(
                    fcmToken.getToken(),
                    command.title(),
                    command.body(),
                    safeData(command.data())
            ));
            applySendResult(fcmToken, delivery, sendResult);
            notificationDeliveryRepository.save(delivery);
        }
    }

    /**
     * FCM 발송 결과를 delivery와 토큰 상태에 반영한다.
     */
    private void applySendResult(FcmToken fcmToken, NotificationDelivery delivery, FcmSendResult sendResult) {
        if (sendResult.success()) {
            delivery.markSuccess(sendResult.providerMessageId(), LocalDateTime.now());
            return;
        }
        delivery.markFailure(sendResult.errorCode(), sendResult.errorMessage());
        if (sendResult.invalidToken()) {
            fcmToken.deactivate(LocalDateTime.now());
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

    /**
     * FCM 메시지에 전달할 빈 데이터 맵을 보정한다.
     */
    private Map<String, String> safeData(Map<String, String> data) {
        if (data == null) {
            return Collections.emptyMap();
        }
        return data;
    }
}
