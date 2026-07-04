package com.autoschedule.workchange.dto;

import com.autoschedule.workchange.domain.WorkChangeRequest;
import com.autoschedule.workchange.domain.WorkChangeRequestStatus;
import com.autoschedule.workchange.domain.WorkChangeRequestType;
import java.time.LocalDateTime;

/**
 * 교대/대타 요청의 현재 처리 상태를 반환한다.
 */
public record WorkChangeRequestResponse(
        Long workChangeRequestId,
        Long workPlaceId,
        WorkChangeRequestType requestType,
        WorkChangeRequestStatus status,
        Long requesterMemberId,
        Long targetMemberId,
        Long requestAssignmentId,
        Long targetAssignmentId,
        String reason,
        LocalDateTime targetRespondedAt,
        Long processedByMemberId,
        LocalDateTime processedAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt
) {

    /**
     * 교대/대타 요청 엔티티를 API 응답 DTO로 변환한다.
     */
    public static WorkChangeRequestResponse from(WorkChangeRequest request) {
        return new WorkChangeRequestResponse(
                request.getId(),
                request.getWorkPlaceId(),
                request.getRequestType(),
                request.getStatus(),
                request.getRequesterMemberId(),
                request.getTargetMemberId(),
                request.getRequestAssignment().getId(),
                request.getTargetAssignment() == null ? null : request.getTargetAssignment().getId(),
                request.getReason(),
                request.getTargetRespondedAt(),
                request.getProcessedByMemberId(),
                request.getProcessedAt(),
                request.getCanceledAt(),
                request.getCreatedAt()
        );
    }
}
