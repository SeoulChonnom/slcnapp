package com.seoulchonnom.aggregate.flow.house;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.house.logic.HouseBatchRunLogic;
import com.seoulchonnom.spec.house.entity.HouseBatchRun;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 단계 하나를 실행하고 house_batch_run에 한 행으로 남긴다. 단계의 예외는 FAILED로 기록하고 밖으로 던지지 않는다.
 * 그래서 한 단계가 실패해도 다음 단계가 진행된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchStepRecorder {
	private final HouseBatchRunLogic houseBatchRunLogic;
	private final Clock houseClock;

	public BatchRunStatus record(LocalDate runDate, BatchStep step, Function<StepProgress, StepOutcome> body) {
		long startedTime = houseClock.millis();
		StepProgress progress = new StepProgress();
		BatchRunStatus status;
		String error = null;
		try {
			StepOutcome outcome = body.apply(progress);
			status = outcome.status();
			error = outcome.error();
		} catch (Exception e) {
			// 예외 메시지에는 외부 요청 URL(서비스 키 포함)이 섞일 수 있어 클래스 이름만 기록한다.
			status = BatchRunStatus.FAILED;
			error = e.getClass().getSimpleName();
			log.error("내집찾기 배치 단계 실패: step={}, error={}", step, error);
		}
		try {
			houseBatchRunLogic.save(HouseBatchRun.builder()
				.runDate(runDate)
				.step(step)
				.status(status)
				.apiCalls(progress.apiCalls())
				.detail(new LinkedHashMap<>(progress.detail()))
				.startedTime(startedTime)
				.finishedTime(houseClock.millis())
				.error(error)
				.build());
		} catch (Exception e) {
			log.error("내집찾기 배치 단계 기록 저장 실패: step={}, error={}", step, e.getClass().getSimpleName());
		}
		log.info("내집찾기 배치 단계 끝: step={}, status={}, apiCalls={}", step, status, progress.apiCalls());
		return status;
	}
}
