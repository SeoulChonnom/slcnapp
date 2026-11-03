package com.seoulchonnom.spec.house.entity;

import java.time.LocalDate;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.house.entity.vo.CollectUnitStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 실거래 수집 단위 (구, 계약년월). 원천 삭제 판정 범위이기도 하다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseCollectUnit extends DomainEntity {
	private String lawdCd;
	/** yyyyMM */
	private String dealYm;
	private CollectUnitStatus status;
	private LocalDate lastSuccessDate;
	/** 연속 실패 횟수. 성공하면 0이 된다. */
	private int attempts;

	public void markSucceeded(LocalDate today) {
		this.status = CollectUnitStatus.DONE;
		this.lastSuccessDate = today;
		this.attempts = 0;
	}

	public void markFailed() {
		this.status = CollectUnitStatus.FAILED;
		this.attempts++;
	}
}
