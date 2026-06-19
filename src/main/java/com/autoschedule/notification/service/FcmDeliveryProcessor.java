package com.autoschedule.notification.service;

import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.FcmTokenStatus;
import com.autoschedule.notification.domain.Notification;
import com.autoschedule.notification.domain.NotificationDelivery;
import com.autoschedule.notification.domain.NotificationDeliveryStatus;
import com.autoschedule.notification.infra.FcmMessage;
import com.autoschedule.notification.infra.FcmSendResult;
import com.autoschedule.notification.infra.FcmSender;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.repository.NotificationDeliveryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING 상태의 FCM delivery를 실제 Firebase 발송 결과로 확정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmDeliveryProcessor {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final TypeReference<Map<String, String>> DATA_TYPE = new TypeReference<>() {
    };

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmSender fcmSender;
    private final ObjectMapper objectMapper;

    /**
     * 전달받은 PENDING delivery 목록을 조회해 FCM 발송 결과를 저장한다.
     */
    @Transactional
    public void process(List<Long> deliveryIds) {
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            return;
        }
        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findByIdInAndStatusWithNotification(
                deliveryIds,
                NotificationDeliveryStatus.PENDING
        );
        Map<Long, FcmToken> activeFcmTokens = findActiveFcmTokens(deliveries);
        for (NotificationDelivery delivery : deliveries) {
            processOne(delivery, activeFcmTokens);
        }
    }

    /**
     * 단일 delivery를 발송하고 성공/실패 결과와 토큰 상태를 반영한다.
     */
    private void processOne(NotificationDelivery delivery, Map<Long, FcmToken> activeFcmTokens) {
        LocalDateTime attemptedAt = LocalDateTime.now();
        delivery.markAttempted(attemptedAt);

        FcmToken fcmToken = findActiveFcmToken(delivery, activeFcmTokens);
        if (fcmToken == null) {
            return;
        }

        try {
            fcmToken.markUsed(attemptedAt);
            FcmSendResult sendResult = fcmSender.send(toMessage(delivery.getNotification(), fcmToken));
            applySendResult(fcmToken, delivery, sendResult);
        } catch (RuntimeException exception) {
            delivery.markFailure("FCM_SEND_EXCEPTION", truncate(exception.getMessage()));
            log.error(
                    "FCM 앱 푸시 발송 중 예외가 발생했습니다. deliveryId={} fcmTokenId={}",
                    delivery.getId(),
                    delivery.getFcmTokenId(),
                    exception
            );
        }
    }

    /**
     * delivery 목록에 포함된 활성 FCM 토큰을 한 번에 조회한다.
     */
    private Map<Long, FcmToken> findActiveFcmTokens(List<NotificationDelivery> deliveries) {
        List<Long> fcmTokenIds = deliveries.stream()
                .map(NotificationDelivery::getFcmTokenId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fcmTokenIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return fcmTokenRepository.findByIdInAndStatus(fcmTokenIds, FcmTokenStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(FcmToken::getId, fcmToken -> fcmToken));
    }

    /**
     * delivery에 연결된 활성 FCM 토큰을 조회하고 없으면 실패 이력으로 확정한다.
     */
    private FcmToken findActiveFcmToken(NotificationDelivery delivery, Map<Long, FcmToken> activeFcmTokens) {
        if (delivery.getFcmTokenId() == null) {
            delivery.markFailure("FCM_TOKEN_NOT_FOUND", "FCM 토큰 ID가 없습니다.");
            log.warn("FCM delivery에 토큰 ID가 없습니다. deliveryId={}", delivery.getId());
            return null;
        }
        FcmToken fcmToken = activeFcmTokens.get(delivery.getFcmTokenId());
        if (fcmToken != null) {
            return fcmToken;
        }
        delivery.markFailure("FCM_TOKEN_NOT_ACTIVE", "활성 FCM 토큰을 찾을 수 없습니다.");
        log.warn(
                "활성 FCM 토큰을 찾을 수 없습니다. deliveryId={} fcmTokenId={}",
                delivery.getId(),
                delivery.getFcmTokenId()
        );
        return null;
    }

    /**
     * FCM 발송 결과를 delivery와 토큰 상태에 반영한다.
     */
    private void applySendResult(FcmToken fcmToken, NotificationDelivery delivery, FcmSendResult sendResult) {
        if (sendResult.success()) {
            delivery.markSuccess(sendResult.providerMessageId(), LocalDateTime.now());
            return;
        }
        delivery.markFailure(sendResult.errorCode(), truncate(sendResult.errorMessage()));
        log.warn(
                "FCM 앱 푸시 발송에 실패했습니다. deliveryId={} fcmTokenId={} errorCode={} errorMessage={}",
                delivery.getId(),
                delivery.getFcmTokenId(),
                sendResult.errorCode(),
                sendResult.errorMessage()
        );
        if (sendResult.invalidToken()) {
            fcmToken.deactivate(LocalDateTime.now());
        }
    }

    /**
     * 알림과 토큰 정보를 Firebase 발송 메시지로 변환한다.
     */
    private FcmMessage toMessage(Notification notification, FcmToken fcmToken) {
        return new FcmMessage(
                fcmToken.getToken(),
                notification.getTitle(),
                notification.getBody(),
                parseData(notification.getData())
        );
    }

    /**
     * 알림 부가 데이터 JSON을 FCM data map으로 변환한다.
     */
    private Map<String, String> parseData(String data) {
        if (data == null || data.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(data, DATA_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("알림 부가 데이터 JSON 변환에 실패했습니다.", exception);
        }
    }

    /**
     * DB 컬럼 길이를 넘지 않도록 오류 메시지를 제한한다.
     */
    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
