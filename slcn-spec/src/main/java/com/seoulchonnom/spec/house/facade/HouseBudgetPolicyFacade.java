package com.seoulchonnom.spec.house.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.house.facade.sdo.BudgetPolicyRdo;

public interface HouseBudgetPolicyFacade {
	/**
	 * 오늘 시행 중인 예산 계산 규칙. 사용자 입력은 받지 않고 FE가 계산한다.
	 */
	ResponseEntity<BudgetPolicyRdo> getBudgetPolicy();
}
