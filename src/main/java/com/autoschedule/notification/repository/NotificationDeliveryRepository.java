package com.autoschedule.notification.repository;

import com.autoschedule.notification.domain.NotificationDelivery;
import com.autoschedule.notification.domain.NotificationDeliveryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 알림 발송 이력 저장과 조회를 담당한다.
 */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    /**
     * 전달받은 ID 목록 중 특정 상태의 발송 이력만 조회한다.
     */
    List<NotificationDelivery> findByIdInAndStatus(List<Long> ids, NotificationDeliveryStatus status);
}
