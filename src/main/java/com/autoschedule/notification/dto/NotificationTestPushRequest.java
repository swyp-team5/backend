package com.autoschedule.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * FCM 발송과 수신 흐름을 수동으로 검증하기 위한 테스트 푸시 요청을 표현한다.
 */
public record NotificationTestPushRequest(
        @NotBlank(message = "테스트 푸시 제목은 필수입니다.")
        @Size(max = 100, message = "테스트 푸시 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "테스트 푸시 본문은 필수입니다.")
        @Size(max = 500, message = "테스트 푸시 본문은 500자 이하여야 합니다.")
        String body,

        @Size(max = 20, message = "테스트 푸시 데이터는 20개 이하여야 합니다.")
        Map<
                @NotBlank(message = "테스트 푸시 데이터 키는 비어 있을 수 없습니다.")
                @Size(max = 50, message = "테스트 푸시 데이터 키는 50자 이하여야 합니다.")
                        String,
                @NotBlank(message = "테스트 푸시 데이터 값은 비어 있을 수 없습니다.")
                @Size(max = 200, message = "테스트 푸시 데이터 값은 200자 이하여야 합니다.")
                        String
                > data
) {
}
