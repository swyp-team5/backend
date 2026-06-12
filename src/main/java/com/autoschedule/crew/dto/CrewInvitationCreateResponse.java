package com.autoschedule.crew.dto;

import java.time.LocalDateTime;

/**
 * 사장님이 생성한 크루 초대 코드 정보를 반환한다.
 */
public record CrewInvitationCreateResponse(
        Long invitationId,
        Long workPlaceId,
        String inviteCode,
        String inviteUrl,
        LocalDateTime expiresAt
) {

    /**
     * 모바일 앱 딥링크 규칙에 맞는 초대 URL을 포함한 응답을 생성한다.
     */
    public static CrewInvitationCreateResponse of(
            Long invitationId,
            Long workPlaceId,
            String inviteCode,
            LocalDateTime expiresAt
    ) {
        return new CrewInvitationCreateResponse(
                invitationId,
                workPlaceId,
                inviteCode,
                "chack-chack://crew-invitations/" + inviteCode,
                expiresAt
        );
    }
}
