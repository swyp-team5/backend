package com.autoschedule.schedule.config;

import com.autoschedule.schedule.generator.ExhaustivePruningScheduleCandidateGenerator;
import com.autoschedule.schedule.generator.ScheduleCandidateGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 자동 스케줄 후보 생성기 Bean 구성을 담당한다.
 */
@Configuration
public class ScheduleGeneratorConfig {

    /**
     * 별도 알고리즘 구현체가 없을 때 MVP 완전탐색 구현체를 기본 생성기로 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(ScheduleCandidateGenerator.class)
    public ScheduleCandidateGenerator scheduleCandidateGenerator() {
        return new ExhaustivePruningScheduleCandidateGenerator();
    }
}
