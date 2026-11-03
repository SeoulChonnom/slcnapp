package com.seoulchonnom.aggregate.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.aggregate.house.collect.HouseSleeper;

@Configuration
public class HouseConfiguration {
	/**
	 * 정책 시행일·통계 기준일은 서울 날짜로 판단한다. 서버 타임존에 기대지 않고 테스트에서 고정할 수 있게 빈으로 둔다.
	 */
	@Bean
	public Clock houseClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

	/**
	 * 재시도 대기. 테스트는 이 빈을 0초짜리로 바꾼다.
	 */
	@Bean
	public HouseSleeper houseSleeper() {
		return duration -> Thread.sleep(duration.toMillis());
	}
}
