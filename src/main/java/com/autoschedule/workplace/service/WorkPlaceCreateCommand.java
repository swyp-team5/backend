package com.autoschedule.workplace.service;

import com.autoschedule.workplace.domain.WorkPlaceSize;

/**
 * 회원가입과 추가 사업장 생성에서 공통으로 사용하는 사업장 생성 명령이다.
 */
public record WorkPlaceCreateCommand(
        WorkPlaceSize size,
        String name,
        String roadAddress,
        String detailAddress,
        String phoneNumber
) {
}
