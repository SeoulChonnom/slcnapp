package com.seoulchonnom.aggregate.flow.house;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.store.HouseBudgetPolicyStore;
import com.seoulchonnom.spec.house.facade.sdo.BudgetPolicyRdo;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapper;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseQueryFlow {
	private final HouseBudgetPolicyStore houseBudgetPolicyStore;
	private final HouseBudgetPolicyMapper houseBudgetPolicyMapper;
	private final Clock houseClock;

	public BudgetPolicyRdo getBudgetPolicy() {
		return houseBudgetPolicyMapper
			.toBudgetPolicyRdo(houseBudgetPolicyStore.findEffective(LocalDate.now(houseClock)));
	}
}
