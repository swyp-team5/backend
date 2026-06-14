package com.autoschedule.notification.infra;

/**
 * FCM 발송 시도 결과를 표현한다.
 */
public record FcmSendResult(
        boolean success,
        String providerMessageId,
        String errorCode,
        String errorMessage,
        boolean invalidToken
) {

    /**
     * FCM 발송 성공 결과를 생성한다.
     */
    public static FcmSendResult success(String providerMessageId) {
        return new FcmSendResult(true, providerMessageId, null, null, false);
    }

    /**
     * FCM 발송 실패 결과를 생성한다.
     */
    public static FcmSendResult failure(String errorCode, String errorMessage, boolean invalidToken) {
        return new FcmSendResult(false, null, errorCode, errorMessage, invalidToken);
    }
}
