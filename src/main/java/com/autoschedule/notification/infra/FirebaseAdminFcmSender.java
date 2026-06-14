package com.autoschedule.notification.infra;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Firebase Admin SDK를 사용해 실제 FCM 메시지를 발송한다.
 */
@RequiredArgsConstructor
public class FirebaseAdminFcmSender implements FcmSender {

    private final FirebaseMessaging firebaseMessaging;

    /**
     * 단일 FCM 토큰으로 알림 메시지를 발송한다.
     */
    @Override
    public FcmSendResult send(FcmMessage message) {
        try {
            String providerMessageId = firebaseMessaging.send(toFirebaseMessage(message));
            return FcmSendResult.success(providerMessageId);
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode messagingErrorCode = exception.getMessagingErrorCode();
            String errorCode = messagingErrorCode == null ? exception.getErrorCode().name() : messagingErrorCode.name();
            return FcmSendResult.failure(
                    errorCode,
                    exception.getMessage(),
                    messagingErrorCode == MessagingErrorCode.UNREGISTERED
            );
        }
    }

    /**
     * 내부 FCM 메시지를 Firebase Admin SDK 메시지로 변환한다.
     */
    private Message toFirebaseMessage(FcmMessage message) {
        Map<String, String> data = message.data() == null ? Collections.emptyMap() : message.data();
        return Message.builder()
                .setToken(message.token())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(data)
                .build();
    }
}
