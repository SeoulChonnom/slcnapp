package com.seoulchonnom.aggregate.flow.house;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.exception.HouseBudgetPolicyNotFoundException;
import com.seoulchonnom.aggregate.house.store.HouseBudgetPolicyStore;
import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;
import com.seoulchonnom.spec.house.facade.sdo.BudgetPolicyRdo;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapperImpl;

class HouseQueryFlowTest {
	// 서울 기준 2026-11-04 00:30 = UTC 2026-11-03 15:30. 시계가 서울 날짜를 써야 한다.
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:30:00Z"), ZoneId.of("Asia/Seoul"));
	private final HouseBudgetPolicyStore store = mock(HouseBudgetPolicyStore.class);
	private final HouseQueryFlow flow = new HouseQueryFlow(store, new HouseBudgetPolicyMapperImpl(), clock);

	@Test
	void getBudgetPolicy_shouldAskStoreWithSeoulToday() {
		HouseBudgetPolicy policy = HouseBudgetPolicy.builder().effectiveFrom(LocalDate.of(2025, 10, 16))
			.ltvFirstTime(new BigDecimal("0.700")).ltvNonFirst(new BigDecimal("0.400"))
			.priceCaps(List.of(new PriceCap(null, 20000))).build();
		when(store.findEffective(LocalDate.of(2026, 11, 4))).thenReturn(policy);

		BudgetPolicyRdo rdo = flow.getBudgetPolicy();

		assertThat(rdo.getLtv()).containsOnlyKeys("FIRST_TIME", "NON_FIRST");
		assertThat(rdo.getLtv().get("FIRST_TIME")).isEqualByComparingTo("0.7");
		assertThat(rdo.getPolicyId()).isEqualTo(policy.getId());
		assertThat(rdo.getPriceCaps()).hasSize(1);
	}

	@Test
	void getBudgetPolicy_shouldPropagateNotFound() {
		when(store.findEffective(any())).thenThrow(new HouseBudgetPolicyNotFoundException());

		assertThatThrownBy(flow::getBudgetPolicy).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}
}
