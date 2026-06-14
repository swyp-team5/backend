package com.autoschedule.notification.infra;

/**
 * Firebase 설정이 비활성화된 환경에서 사용하는 FCM sender다.
 */
public class DisabledFcmSender implements FcmSender {

    private static final String DISABLED_ERROR_CODE = "FCM_DISABLED";
    private static final String DISABLED_ERROR_MESSAGE = "FCM 발송 설정이 비활성화되어 있습니다.";

    /**
     * 발송을 시도하지 않고 비활성화 실패 결과를 반환한다.
     */
    @Override
    public FcmSendResult send(FcmMessage message) {
        return FcmSendResult.failure(DISABLED_ERROR_CODE, DISABLED_ERROR_MESSAGE, false);
    }
}
