package com.autoschedule.notification.infra;

/**
 * FCM 메시지 발송을 추상화한다.
 */
public interface FcmSender {

    /**
     * 단일 FCM 토큰으로 메시지를 발송한다.
     */
    FcmSendResult send(FcmMessage message);
}
