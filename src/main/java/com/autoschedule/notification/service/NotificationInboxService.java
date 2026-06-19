package com.autoschedule.notification.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.Notification;
import com.autoschedule.notification.domain.NotificationStatus;
import com.autoschedule.notification.dto.NotificationCursorResponse;
import com.autoschedule.notification.dto.NotificationResponse;
import com.autoschedule.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 알림함 조회와 읽음 처리를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private static final int MAX_NOTIFICATION_SCROLL_SIZE = 100;
    private static final long FIRST_CURSOR_ID = Long.MAX_VALUE;

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 회원 본인의 알림함을 최신순 커서 방식으로 조회한다.
     */
    @Transactional(readOnly = true)
    public NotificationCursorResponse getNotifications(Long memberId, Long cursorId, int size) {
        findActiveMember(memberId);
        long normalizedCursorId = cursorId == null ? FIRST_CURSOR_ID : cursorId;
        int normalizedSize = Math.min(size, MAX_NOTIFICATION_SCROLL_SIZE);
        List<Notification> notifications = notificationRepository
                .findByReceiverMember_IdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        memberId,
                        NotificationStatus.ACTIVE,
                        normalizedCursorId,
                        PageRequest.of(0, normalizedSize + 1)
                );

        boolean hasNext = notifications.size() > normalizedSize;
        List<Notification> visibleNotifications = hasNext
                ? notifications.subList(0, normalizedSize)
                : notifications;
        List<NotificationResponse> content = visibleNotifications.stream()
                .map(notification -> NotificationResponse.from(notification, parseData(notification.getData())))
                .toList();
        Long nextCursorId = hasNext && !visibleNotifications.isEmpty()
                ? visibleNotifications.get(visibleNotifications.size() - 1).getId()
                : null;

        return new NotificationCursorResponse(content, nextCursorId, hasNext);
    }

    /**
     * 회원 본인의 알림 단건을 읽음 처리한다.
     */
    @Transactional
    public NotificationResponse markAsRead(Long memberId, Long notificationId) {
        findActiveMember(memberId);
        Notification notification = notificationRepository
                .findByIdAndReceiverMember_IdAndStatusAndDeletedAtIsNull(
                        notificationId,
                        memberId,
                        NotificationStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "알림을 찾을 수 없습니다."));
        notification.markRead(LocalDateTime.now());
        return NotificationResponse.from(notification, parseData(notification.getData()));
    }

    /**
     * 회원 본인의 모든 미읽음 알림을 읽음 처리한다.
     */
    @Transactional
    public void markAllAsRead(Long memberId) {
        findActiveMember(memberId);
        notificationRepository.markAllUnreadAsRead(
                memberId,
                NotificationStatus.ACTIVE,
                LocalDateTime.now()
        );
    }

    /**
     * 인증 주체가 활성 회원인지 확인한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * JSON 문자열 data를 응답용 JsonNode로 변환한다.
     */
    private JsonNode parseData(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(data);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "알림 부가 데이터 형식이 올바르지 않습니다.");
        }
    }
}
