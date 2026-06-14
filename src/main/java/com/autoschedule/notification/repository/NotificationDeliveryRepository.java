package com.autoschedule.notification.repository;

import com.autoschedule.notification.domain.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 알림 발송 이력 저장과 조회를 담당한다.
 */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
}
