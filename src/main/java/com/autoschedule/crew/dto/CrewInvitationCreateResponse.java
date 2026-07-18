package com.autoschedule.crew.dto;

import java.time.LocalDateTime;

/**
 * 사장님이 생성한 크루 초대 코드 정보를 반환한다.
 */
public record CrewInvitationCreateResponse(
        Long invitationId,
        Long workPlaceId,
        String inviteCode,

        /**
         * 기존 iOS 앱 호환을 위한 커스텀 스킴 딥링크.
         *
         * 예: chack-chack://crew-invitations/123456
         */
        String inviteUrl,

        /**
         * 카카오톡, 문자, SNS 등에 공유하는 HTTPS URL.
         *
         * 예: https://chackchack.shop/crew-invitations/123456
         */
        String inviteShareUrl,
        LocalDateTime expiresAt
) {

    private static final String APP_DEEP_LINK_PREFIX =
        "chack-chack://crew-invitations/";

    private static final String SHARE_URL_PREFIX =
        "https://chackchack.shop/crew-invitations/";

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
            APP_DEEP_LINK_PREFIX + inviteCode,
            SHARE_URL_PREFIX + inviteCode,
                expiresAt
        );
    }
}
