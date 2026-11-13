package com.seoulchonnom.aggregate.house.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.exception.HouseBudgetPolicyNotFoundException;
import com.seoulchonnom.aggregate.house.store.jpo.HouseBudgetPolicyJpo;
import com.seoulchonnom.aggregate.house.store.mapper.HouseBudgetPolicyJpoMapper;
import com.seoulchonnom.aggregate.house.store.mapper.HouseBudgetPolicyJpoMapperImpl;
import com.seoulchonnom.aggregate.house.store.repository.HouseBudgetPolicyRepository;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;
import com.seoulchonnom.spec.house.entity.vo.AcquisitionTax;
import com.seoulchonnom.spec.house.entity.vo.Brokerage;
import com.seoulchonnom.spec.house.entity.vo.BrokerageTier;
import com.seoulchonnom.spec.house.entity.vo.CostRates;
import com.seoulchonnom.spec.house.entity.vo.FirstTimeRelief;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;

class HouseBudgetPolicyStoreTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

	private final HouseBudgetPolicyRepository repository = mock(HouseBudgetPolicyRepository.class);
	private final HouseBudgetPolicyJpoMapper jpoMapper = new HouseBudgetPolicyJpoMapperImpl();
	private final HouseBudgetPolicyStore store = new HouseBudgetPolicyStore(repository, jpoMapper);

	private HouseBudgetPolicyJpo jpo(Consumer<HouseBudgetPolicyJpo> customizer) {
		HouseBudgetPolicyJpo jpo = new HouseBudgetPolicyJpo();
		jpo.setId("p1");
		jpo.setEffectiveFrom(LocalDate.of(2025, 10, 16));
		jpo.setDsrRatio(new BigDecimal("0.400"));
		jpo.setStressRatePp(new BigDecimal("3.00"));
		jpo.setTermYears(30);
		jpo.setDefaultReserveManwon(2000);
		jpo.setLtvFirstTime(new BigDecimal("0.700"));
		jpo.setLtvNonFirst(new BigDecimal("0.400"));
		jpo.setPriceCaps(new ArrayList<>(List.of(new PriceCap(150000L, 60000), new PriceCap(250000L, 40000),
			new PriceCap(null, 20000))));
		jpo.setCostRates(new CostRates(
			new AcquisitionTax(new BigDecimal("1.0"), 60000, new BigDecimal("3.0"), 90000, new BigDecimal("0.1")),
			new FirstTimeRelief(200, 120000),
			new Brokerage(new ArrayList<>(List.of(new BrokerageTier(5000L, new BigDecimal("0.6"), 25L),
				new BrokerageTier(20000L, new BigDecimal("0.5"), 80L),
				new BrokerageTier(null, new BigDecimal("0.7"), null))), new BigDecimal("0.1")),
			new BigDecimal("0.2")));
		jpo.setSourceNote("note");
		customizer.accept(jpo);
		return jpo;
	}

	private void stub(Consumer<HouseBudgetPolicyJpo> customizer) {
		when(repository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(TODAY))
			.thenReturn(Optional.of(jpo(customizer)));
	}

	@Test
	void findEffective_shouldReturnPolicyWhenRulesHold() {
		stub(j -> {
		});

		HouseBudgetPolicy policy = store.findEffective(TODAY);

		assertThat(policy.getId()).isEqualTo("p1");
		assertThat(policy.getPriceCaps()).hasSize(3);
		assertThat(policy.getCostRates().getBrokerage().getTiers()).hasSize(3);
	}

	@Test
	void findEffective_shouldThrow503WhenNoRow() {
		when(repository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.HOUSE_BUDGET_POLICY_NOT_FOUND);
	}

	@Test
	void findEffective_shouldThrowWhenPriceCapsNotAscending() {
		stub(j -> j.setPriceCaps(List.of(new PriceCap(250000L, 40000), new PriceCap(150000L, 60000),
			new PriceCap(null, 20000))));

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}

	@Test
	void findEffective_shouldThrowWhenPriceCapsLastNotNull() {
		stub(j -> j.setPriceCaps(List.of(new PriceCap(150000L, 60000), new PriceCap(250000L, 40000))));

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}

	@Test
	void findEffective_shouldThrowWhenPriceCapsNullInMiddle() {
		stub(j -> j.setPriceCaps(List.of(new PriceCap(null, 60000), new PriceCap(null, 20000))));

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}

	@Test
	void findEffective_shouldThrowWhenPriceCapsEmpty() {
		stub(j -> j.setPriceCaps(List.of()));

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}

	@Test
	void findEffective_shouldThrowWhenBrokerageTiersNotAscending() {
		stub(j -> j.getCostRates().getBrokerage().setTiers(List.of(
			new BrokerageTier(20000L, new BigDecimal("0.5"), 80L), new BrokerageTier(5000L, new BigDecimal("0.6"), 25L),
			new BrokerageTier(null, new BigDecimal("0.7"), null))));

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}

	@Test
	void findEffective_shouldThrowWhenBrokerageTiersLastNotNull() {
		stub(j -> j.getCostRates().getBrokerage().setTiers(List.of(
			new BrokerageTier(5000L, new BigDecimal("0.6"), 25L), new BrokerageTier(20000L, new BigDecimal("0.5"), 80L))));

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}

	@Test
	void findEffective_shouldThrowWhenCostRatesMissing() {
		stub(j -> j.setCostRates(null));

		assertThatThrownBy(() -> store.findEffective(TODAY)).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}
}
