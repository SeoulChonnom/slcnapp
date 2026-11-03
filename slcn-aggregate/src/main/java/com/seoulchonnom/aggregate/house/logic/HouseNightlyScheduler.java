package com.seoulchonnom.aggregate.house.logic;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.flow.house.HouseNightlyFlow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내집찾기 야간 배치 진입점. 실제 작업과 단계 기록은 HouseNightlyFlow가 한다.
 * 스케줄러 스레드는 하나라서 예외를 밖으로 던지지 않는다(기록은 Flow가 단계별로 남긴다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HouseNightlyScheduler {
	private final HouseNightlyFlow houseNightlyFlow;
	private final Clock houseClock;

	/**
	 * 설정값이 없으면 "-"로 꺼진다. 운영 값은 application.yml에 둔다.
	 */
	@Scheduled(cron = "${slcn.house.nightly-cron:-}", zone = "Asia/Seoul")
	public void runNightly() {
		try {
			houseNightlyFlow.run(LocalDate.now(houseClock));
		} catch (Exception e) {
			// 예외 메시지에 외부 요청 URL이 섞일 수 있어 클래스 이름만 남긴다.
			log.error("내집찾기 야간 배치가 예외로 끝났습니다: {}", e.getClass().getSimpleName());
		}
	}
}
