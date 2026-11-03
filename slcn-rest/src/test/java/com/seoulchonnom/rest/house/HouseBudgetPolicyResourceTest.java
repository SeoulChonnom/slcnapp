package com.seoulchonnom.rest.house;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.flow.house.HouseQueryFlow;
import com.seoulchonnom.aggregate.house.exception.HouseBudgetPolicyNotFoundException;
import com.seoulchonnom.aggregate.house.store.HouseBudgetPolicyStore;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;
import com.seoulchonnom.spec.house.entity.vo.AcquisitionTax;
import com.seoulchonnom.spec.house.entity.vo.Brokerage;
import com.seoulchonnom.spec.house.entity.vo.BrokerageTier;
import com.seoulchonnom.spec.house.entity.vo.CostRates;
import com.seoulchonnom.spec.house.entity.vo.FirstTimeRelief;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapperImpl;

class HouseBudgetPolicyResourceTest {
	private HouseBudgetPolicyStore store;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		store = mock(HouseBudgetPolicyStore.class);
		Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		HouseQueryFlow flow = new HouseQueryFlow(store, new HouseBudgetPolicyMapperImpl(), clock);
		mockMvc = MockMvcBuilders.standaloneSetup(new HouseBudgetPolicyResource(flow))
			.setControllerAdvice(new CommonExceptionHandler()).build();
	}

	@Test
	void getBudgetPolicy_shouldRenderSpecShape() throws Exception {
		HouseBudgetPolicy policy = HouseBudgetPolicy.builder()
			.effectiveFrom(LocalDate.of(2025, 10, 16)).dsrRatio(new BigDecimal("0.400"))
			.stressRatePp(new BigDecimal("3.00")).termYears(30).defaultReserveManwon(2000)
			.ltvFirstTime(new BigDecimal("0.700")).ltvNonFirst(new BigDecimal("0.400"))
			.priceCaps(List.of(new PriceCap(150000L, 60000), new PriceCap(null, 20000)))
			.costRates(new CostRates(
				new AcquisitionTax(new BigDecimal("1.0"), 60000, new BigDecimal("3.0"), 90000, new BigDecimal("0.1")),
				new FirstTimeRelief(200, 120000),
				new Brokerage(List.of(new BrokerageTier(5000L, new BigDecimal("0.6"), 25L),
					new BrokerageTier(null, new BigDecimal("0.7"), null)), new BigDecimal("0.1")),
				new BigDecimal("0.2")))
			.sourceNote("10·15 대책").build();
		policy.setId("policy-1");
		when(store.findEffective(LocalDate.of(2026, 11, 3))).thenReturn(policy);

		mockMvc.perform(get("/house/budget-policy"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyId").value("policy-1"))
			.andExpect(jsonPath("$.effectiveFrom").value("2025-10-16"))
			.andExpect(jsonPath("$.dsrRatio").value(0.4))
			.andExpect(jsonPath("$.stressRatePp").value(3.0))
			.andExpect(jsonPath("$.termYears").value(30))
			.andExpect(jsonPath("$.defaultReserveManwon").value(2000))
			.andExpect(jsonPath("$.ltv.FIRST_TIME").value(0.7))
			.andExpect(jsonPath("$.ltv.NON_FIRST").value(0.4))
			.andExpect(jsonPath("$.priceCaps[0].maxPriceManwon").value(150000))
			.andExpect(jsonPath("$.priceCaps[0].capManwon").value(60000))
			.andExpect(jsonPath("$.priceCaps[1].maxPriceManwon").doesNotExist())
			.andExpect(jsonPath("$.priceCaps[1].capManwon").value(20000))
			.andExpect(jsonPath("$.costRates.acquisitionTax.lowRatePct").value(1.0))
			.andExpect(jsonPath("$.costRates.acquisitionTax.lowMaxManwon").value(60000))
			.andExpect(jsonPath("$.costRates.acquisitionTax.highRatePct").value(3.0))
			.andExpect(jsonPath("$.costRates.acquisitionTax.highMinManwon").value(90000))
			.andExpect(jsonPath("$.costRates.acquisitionTax.localEducationRatio").value(0.1))
			.andExpect(jsonPath("$.costRates.firstTimeRelief.capManwon").value(200))
			.andExpect(jsonPath("$.costRates.firstTimeRelief.maxPriceManwon").value(120000))
			.andExpect(jsonPath("$.costRates.brokerage.tiers[0].belowManwon").value(5000))
			.andExpect(jsonPath("$.costRates.brokerage.tiers[0].ratePct").value(0.6))
			.andExpect(jsonPath("$.costRates.brokerage.tiers[0].capManwon").value(25))
			.andExpect(jsonPath("$.costRates.brokerage.tiers[1].belowManwon").isEmpty())
			.andExpect(jsonPath("$.costRates.brokerage.tiers[1].capManwon").isEmpty())
			.andExpect(jsonPath("$.costRates.brokerage.vatRatio").value(0.1))
			.andExpect(jsonPath("$.costRates.registrationRatePct").value(0.2))
			.andExpect(jsonPath("$.sourceNote").value("10·15 대책"));
	}

	@Test
	void getBudgetPolicy_shouldRender503WhenNoPolicy() throws Exception {
		when(store.findEffective(any())).thenThrow(new HouseBudgetPolicyNotFoundException());

		mockMvc.perform(get("/house/budget-policy"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("HOUSE_BUDGET_POLICY_NOT_FOUND"));
	}
}
