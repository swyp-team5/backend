package com.autoschedule.notification.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림/푸시 비동기 작업에 사용하는 전용 실행기를 구성한다.
 */
@Configuration
public class NotificationAsyncConfig {

    /**
     * FCM 앱 푸시 발송이 일반 요청 처리 스레드와 섞이지 않도록 전용 스레드 풀을 제공한다.
     */
    @Bean(name = "notificationPushExecutor")
    public Executor notificationPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-push-");
        executor.initialize();
        return executor;
    }
}
