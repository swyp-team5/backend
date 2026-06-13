package com.autoschedule.crew.dto;

import com.autoschedule.crew.domain.CrewInvitation;
import com.autoschedule.crew.domain.CrewInvitationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * 사장님이 조회하는 사업장 초대 코드 발급/사용 이력을 반환한다.
 */
public record CrewInvitationHistoryResponse(
        List<Item> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * 초대 코드 페이지와 수락자 이름 스냅샷을 모바일 응답으로 변환한다.
     */
    public static CrewInvitationHistoryResponse from(
            Page<CrewInvitation> invitations,
            Map<Long, String> usedMemberNames
    ) {
        List<Item> content = invitations.getContent()
                .stream()
                .map(invitation -> Item.from(invitation, resolveUsedMemberName(invitation, usedMemberNames)))
                .toList();

        return new CrewInvitationHistoryResponse(
                content,
                invitations.getNumber(),
                invitations.getSize(),
                invitations.getTotalElements(),
                invitations.getTotalPages()
        );
    }

    /**
     * 수락자가 없는 초대 코드는 회원명 조회를 시도하지 않고 null로 반환한다.
     */
    private static String resolveUsedMemberName(CrewInvitation invitation, Map<Long, String> usedMemberNames) {
        Long usedByMemberId = invitation.getUsedByMemberId();
        if (usedByMemberId == null) {
            return null;
        }
        return usedMemberNames.get(usedByMemberId);
    }

    /**
     * 초대 코드 1건의 발급 상태와 사용 정보를 반환한다.
     */
    public record Item(
            Long invitationId,
            Long workPlaceId,
            String inviteCode,
            String inviteUrl,
            CrewInvitationStatus status,
            LocalDateTime expiresAt,
            LocalDateTime usedAt,
            Long usedByMemberId,
            String usedByMemberName,
            int failedAttemptCount,
            LocalDateTime createdAt
    ) {

        private static final String INVITE_URL_PREFIX = "chack-chack://crew-invitations/";

        /**
         * 초대 코드 엔티티를 이력 조회 항목 응답으로 변환한다.
         */
        private static Item from(CrewInvitation invitation, String usedByMemberName) {
            return new Item(
                    invitation.getId(),
                    invitation.getWorkPlace().getId(),
                    invitation.getInviteCode(),
                    INVITE_URL_PREFIX + invitation.getInviteCode(),
                    invitation.getStatus(),
                    invitation.getExpiresAt(),
                    invitation.getUsedAt(),
                    invitation.getUsedByMemberId(),
                    usedByMemberName,
                    invitation.getFailedAttemptCount(),
                    invitation.getCreatedAt()
            );
        }
    }
}
