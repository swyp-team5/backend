package com.autoschedule.notification.repository;

import com.autoschedule.notification.domain.Notification;
import com.autoschedule.notification.domain.NotificationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 알림함 저장과 조회를 담당한다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 회원의 활성 알림을 notificationId 역순 커서 방식으로 조회한다.
     */
    List<Notification> findByReceiverMember_IdAndStatusAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long receiverMemberId,
            NotificationStatus status,
            Long cursorId,
            Pageable pageable
    );

    /**
     * 회원 본인의 활성 알림 단건을 조회한다.
     */
    Optional<Notification> findByIdAndReceiverMember_IdAndStatusAndDeletedAtIsNull(
            Long id,
            Long receiverMemberId,
            NotificationStatus status
    );

    /**
     * 회원의 모든 미읽음 활성 알림을 읽음 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.readAt = :readAt
             where notification.receiverMember.id = :receiverMemberId
               and notification.status = :status
               and notification.deletedAt is null
               and notification.readAt is null
            """)
    void markAllUnreadAsRead(
            @Param("receiverMemberId") Long receiverMemberId,
            @Param("status") NotificationStatus status,
            @Param("readAt") LocalDateTime readAt
    );
}
