package com.autoschedule.workchange.dto;

import com.autoschedule.workchange.domain.WorkChangeRequest;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 교대/대타 요청 목록 페이지 응답을 표현한다.
 */
public record WorkChangeRequestPageResponse(
        List<WorkChangeRequestResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * 교대/대타 요청 페이지를 API 응답 DTO로 변환한다.
     */
    public static WorkChangeRequestPageResponse from(Page<WorkChangeRequest> requests) {
        return new WorkChangeRequestPageResponse(
                requests.getContent().stream()
                        .map(WorkChangeRequestResponse::from)
                        .toList(),
                requests.getNumber(),
                requests.getSize(),
                requests.getTotalElements(),
                requests.getTotalPages()
        );
    }
}
