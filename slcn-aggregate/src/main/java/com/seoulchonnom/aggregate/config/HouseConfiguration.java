package com.seoulchonnom.aggregate.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HouseConfiguration {
	/**
	 * 정책 시행일·통계 기준일은 서울 날짜로 판단한다. 서버 타임존에 기대지 않고 테스트에서 고정할 수 있게 빈으로 둔다.
	 */
	@Bean
	public Clock houseClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
