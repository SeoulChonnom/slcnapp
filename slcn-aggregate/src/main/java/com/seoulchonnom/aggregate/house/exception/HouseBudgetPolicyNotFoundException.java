package com.seoulchonnom.aggregate.house.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class HouseBudgetPolicyNotFoundException extends BusinessException {
	public HouseBudgetPolicyNotFoundException() {
		super(ErrorCode.HOUSE_BUDGET_POLICY_NOT_FOUND);
	}

	public HouseBudgetPolicyNotFoundException(String message) {
		super(ErrorCode.HOUSE_BUDGET_POLICY_NOT_FOUND, message);
	}
}
