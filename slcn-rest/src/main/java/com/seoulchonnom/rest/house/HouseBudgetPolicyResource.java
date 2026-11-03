package com.seoulchonnom.rest.house;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.house.HouseQueryFlow;
import com.seoulchonnom.spec.house.facade.HouseBudgetPolicyFacade;
import com.seoulchonnom.spec.house.facade.sdo.BudgetPolicyRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/house/budget-policy")
@RequiredArgsConstructor
public class HouseBudgetPolicyResource implements HouseBudgetPolicyFacade {
	private final HouseQueryFlow houseQueryFlow;

	@Override
	@GetMapping
	public ResponseEntity<BudgetPolicyRdo> getBudgetPolicy() {
		return new ResponseEntity<>(houseQueryFlow.getBudgetPolicy(), HttpStatus.OK);
	}
}
