package com.autoschedule.notification.config;

import com.autoschedule.notification.infra.DisabledFcmSender;
import com.autoschedule.notification.infra.FcmSender;
import com.autoschedule.notification.infra.FirebaseAdminFcmSender;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * FCM 발송 구현체를 환경 설정에 따라 구성한다.
 */
@Configuration
@EnableConfigurationProperties(NotificationFcmProperties.class)
public class FcmSenderConfig {

    private static final String FIREBASE_APP_NAME = "autoschedule-fcm";

    /**
     * Firebase 설정이 활성화된 경우 실제 sender를, 아니면 disabled sender를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(FcmSender.class)
    public FcmSender fcmSender(NotificationFcmProperties properties) {
        if (!properties.enabled()) {
            return new DisabledFcmSender();
        }
        return new FirebaseAdminFcmSender(FirebaseMessaging.getInstance(initializeFirebaseApp(properties)));
    }

    /**
     * Firebase Admin SDK 앱을 초기화하거나 기존 앱을 재사용한다.
     */
    private FirebaseApp initializeFirebaseApp(NotificationFcmProperties properties) {
        List<FirebaseApp> apps = FirebaseApp.getApps();
        for (FirebaseApp app : apps) {
            if (FIREBASE_APP_NAME.equals(app.getName())) {
                return app;
            }
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(loadCredentials(properties))
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (IOException exception) {
            throw new IllegalStateException("Firebase Admin SDK 초기화에 실패했습니다.", exception);
        }
    }

    /**
     * 명시된 서비스 계정 경로 또는 Application Default Credentials를 로드한다.
     */
    private GoogleCredentials loadCredentials(NotificationFcmProperties properties) throws IOException {
        if (StringUtils.hasText(properties.credentialsPath())) {
            try (FileInputStream inputStream = new FileInputStream(properties.credentialsPath())) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
