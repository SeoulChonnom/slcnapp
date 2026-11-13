package com.seoulchonnom.spec.house.entity;

import java.time.LocalDate;
import java.util.Map;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 야간 배치 한 단계의 실행 기록. 한 실행에서 단계마다 1행이다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseBatchRun extends DomainEntity {
	private LocalDate runDate;
	private BatchStep step;
	private BatchRunStatus status;
	private int apiCalls;
	/** 단계별 건수. */
	private Map<String, Object> detail;
	private long startedTime;
	private long finishedTime;
	/** 예외 클래스명과 우리가 만든 메시지만. 외부 응답 본문·URL은 넣지 않는다. */
	private String error;
}
