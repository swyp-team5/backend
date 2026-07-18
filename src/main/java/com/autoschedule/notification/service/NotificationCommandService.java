package com.autoschedule.notification.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.FcmTokenStatus;
import com.autoschedule.notification.domain.MemberNotificationSetting;
import com.autoschedule.notification.domain.Notification;
import com.autoschedule.notification.domain.NotificationDelivery;
import com.autoschedule.notification.domain.PushPolicy;
import com.autoschedule.notification.dto.NotificationSendCommand;
import com.autoschedule.notification.event.NotificationPushRequestedEvent;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.repository.MemberNotificationSettingRepository;
import com.autoschedule.notification.repository.NotificationDeliveryRepository;
import com.autoschedule.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final MemberNotificationSettingRepository memberNotificationSettingRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 단일 회원에게 알림을 저장하고 PUSH 정책이면 커밋 이후 FCM 발송 이벤트를 발행한다.
     */
    @Transactional
    public Long sendToMember(Long receiverMemberId, NotificationSendCommand command) {
        List<Long> notificationIds = sendToMembers(List.of(receiverMemberId), command);
        return notificationIds.get(0);
    }

    /**
     * 여러 회원에게 알림을 저장하고 PUSH 정책이면 커밋 이후 FCM 발송 이벤트를 한 번만 발행한다.
     */
    @Transactional
    public List<Long> sendToMembers(List<Long> receiverMemberIds, NotificationSendCommand command) {
        List<Long> distinctReceiverIds = normalizeReceiverIds(receiverMemberIds);
        if (distinctReceiverIds.isEmpty()) {
            return List.of();
        }

        List<Member> receivers = findActiveMembers(distinctReceiverIds);
        List<Notification> notifications = createNotifications(receivers, command);
        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);

        if (command.pushPolicy() == PushPolicy.PUSH) {
            publishPushEventIfNeeded(createPendingDeliveries(savedNotifications, distinctReceiverIds));
        }
        return savedNotifications.stream()
                .map(Notification::getId)
                .toList();
    }

    /**
     * 수신자 ID 목록에서 중복을 제거하고 null 값은 거부한다.
     */
    private List<Long> normalizeReceiverIds(List<Long> receiverMemberIds) {
        if (receiverMemberIds == null || receiverMemberIds.isEmpty()) {
            return List.of();
        }
        if (receiverMemberIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "알림 수신 회원 ID가 올바르지 않습니다.");
        }
        return receiverMemberIds.stream()
                .distinct()
                .toList();
    }

    /**
     * 요청된 수신자가 모두 활성 회원인지 일괄 조회로 확인한다.
     */
    private List<Member> findActiveMembers(List<Long> receiverMemberIds) {
        Map<Long, Member> membersById = memberRepository.findActiveByIdIn(receiverMemberIds)
                .stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        if (membersById.size() != receiverMemberIds.size()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "알림 수신 회원을 찾을 수 없습니다.");
        }
        return receiverMemberIds.stream()
                .map(membersById::get)
                .toList();
    }

    /**
     * 회원별 알림 엔티티를 생성한다.
     */
    private List<Notification> createNotifications(List<Member> receivers, NotificationSendCommand command) {
        String dataJson = toJson(command.data());
        return receivers.stream()
                .map(receiver -> Notification.create(
                        receiver,
                        command.notificationType(),
                        command.pushPolicy(),
                        command.title(),
                        command.body(),
                        dataJson
                ))
                .toList();
    }

    /**
     * 수신자별 활성 FCM 토큰마다 PENDING delivery를 생성한다.
     */
    private List<Long> createPendingDeliveries(List<Notification> notifications, List<Long> receiverMemberIds) {
        Set<Long> fcmPushEnabledMemberIds = findFcmPushEnabledMemberIds(receiverMemberIds);
        Map<Long, List<FcmToken>> fcmTokensByMemberId = findActiveFcmTokensByMemberId(receiverMemberIds);
        List<NotificationDelivery> deliveries = new ArrayList<>();
        for (Notification notification : notifications) {
            Long receiverMemberId = notification.getReceiverMember().getId();
            if (!fcmPushEnabledMemberIds.contains(receiverMemberId)) {
                continue;
            }
            List<FcmToken> fcmTokens = fcmTokensByMemberId.getOrDefault(receiverMemberId, List.of());
            for (FcmToken fcmToken : fcmTokens) {
                deliveries.add(NotificationDelivery.createFcmPending(
                        notification,
                        fcmToken.getId(),
                        null
                ));
            }
        }
        return notificationDeliveryRepository.saveAll(deliveries)
                .stream()
                .map(NotificationDelivery::getId)
                .toList();
    }

    /**
     * 수신자들의 활성 FCM 토큰을 한 번에 조회한 뒤 회원 ID 기준으로 묶는다.
     */
    private Map<Long, List<FcmToken>> findActiveFcmTokensByMemberId(List<Long> receiverMemberIds) {
        List<FcmToken> fcmTokens = fcmTokenRepository.findByMember_IdInAndStatus(
                receiverMemberIds,
                FcmTokenStatus.ACTIVE
        );
        Map<Long, List<FcmToken>> tokensByMemberId = new LinkedHashMap<>();
        for (FcmToken fcmToken : fcmTokens) {
            tokensByMemberId
                    .computeIfAbsent(fcmToken.getMember().getId(), ignored -> new ArrayList<>())
                    .add(fcmToken);
        }
        return tokensByMemberId;
    }

    /**
     * 생성된 delivery가 있을 때만 커밋 이후 FCM 발송 이벤트를 발행한다.
     */
    /**
     * FCM 푸시 수신을 허용한 회원 ID만 추린다. 설정이 없는 기존 회원은 기본 허용으로 본다.
     */
    private Set<Long> findFcmPushEnabledMemberIds(List<Long> receiverMemberIds) {
        Map<Long, MemberNotificationSetting> settingsByMemberId = memberNotificationSettingRepository
                .findByMember_IdIn(receiverMemberIds)
                .stream()
                .collect(Collectors.toMap(setting -> setting.getMember().getId(), Function.identity()));

        return receiverMemberIds.stream()
                .filter(memberId -> {
                    MemberNotificationSetting setting = settingsByMemberId.get(memberId);
                    return setting == null || setting.isFcmPushEnabled();
                })
                .collect(Collectors.toSet());
    }

    private void publishPushEventIfNeeded(List<Long> deliveryIds) {
        if (!deliveryIds.isEmpty()) {
            eventPublisher.publishEvent(new NotificationPushRequestedEvent(deliveryIds));
        }
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
