package com.autoschedule.notification.event;

import com.autoschedule.notification.service.FcmDeliveryProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장 트랜잭션 커밋 이후 FCM 앱 푸시 발송 처리를 시작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushEventListener {

    private final FcmDeliveryProcessor fcmDeliveryProcessor;

    /**
     * 커밋된 delivery 목록을 비동기로 발송 처리하고, 리스너 예외는 로그로만 남긴다.
     */
    @Async("notificationPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationPushRequestedEvent event) {
        try {
            fcmDeliveryProcessor.process(event.deliveryIds());
        } catch (RuntimeException exception) {
            log.error("FCM 앱 푸시 이벤트 처리에 실패했습니다. deliveryIds={}", event.deliveryIds(), exception);
        }
    }
}
