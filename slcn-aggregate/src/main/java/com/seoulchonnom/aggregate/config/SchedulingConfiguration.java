package com.seoulchonnom.aggregate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 작업을 켠다. 이 설정이 없으면 @Scheduled는 오류 없이 조용히 무시된다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
