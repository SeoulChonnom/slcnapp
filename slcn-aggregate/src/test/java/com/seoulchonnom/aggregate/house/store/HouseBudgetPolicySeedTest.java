package com.seoulchonnom.aggregate.house.store;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.store.jpo.converter.CostRatesConverter;
import com.seoulchonnom.aggregate.house.store.jpo.converter.PriceCapListConverter;
import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;
import com.seoulchonnom.spec.house.entity.vo.CostRates;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;

/**
 * 시드 SQL의 JSON 문자열을 그대로 컨버터에 넣는다. 키 이름이 VO 필드와 어긋나면
 * FAIL_ON_UNKNOWN_PROPERTIES가 꺼져 있어 조용히 null이 되므로, 값까지 확인한다.
 */
class HouseBudgetPolicySeedTest {
	private static final Path SEED = Path.of("..", "docs", "myseoulhouse", "sql", "house_budget_policy_seed.sql");

	private List<String> jsonLiterals() throws IOException {
		String sql = Files.readString(SEED);
		Matcher matcher = Pattern.compile("'([\\[{][^']*)'").matcher(sql);
		List<String> found = new ArrayList<>();
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	@Test
	void seedPriceCaps_shouldConvertToExpectedValues() throws IOException {
		List<PriceCap> caps = new PriceCapListConverter().convertToEntityAttribute(jsonLiterals().get(0));

		assertThat(caps).containsExactly(new PriceCap(150000L, 60000), new PriceCap(250000L, 40000),
			new PriceCap(null, 20000));
	}

	@Test
	void seedCostRates_shouldConvertEveryField() throws IOException {
		CostRates rates = new CostRatesConverter().convertToEntityAttribute(jsonLiterals().get(1));

		assertThat(rates.getAcquisitionTax().getLowRatePct()).isEqualByComparingTo("1.0");
		assertThat(rates.getAcquisitionTax().getLowMaxManwon()).isEqualTo(60000);
		assertThat(rates.getAcquisitionTax().getHighRatePct()).isEqualByComparingTo("3.0");
		assertThat(rates.getAcquisitionTax().getHighMinManwon()).isEqualTo(90000);
		assertThat(rates.getAcquisitionTax().getLocalEducationRatio()).isEqualByComparingTo("0.1");
		assertThat(rates.getFirstTimeRelief().getCapManwon()).isEqualTo(200);
		assertThat(rates.getFirstTimeRelief().getMaxPriceManwon()).isEqualTo(120000);
		assertThat(rates.getBrokerage().getVatRatio()).isEqualByComparingTo("0.1");
		assertThat(rates.getRegistrationRatePct()).isEqualByComparingTo("0.2");
		assertThat(rates.getBrokerage().getTiers()).hasSize(6);
		assertThat(rates.getBrokerage().getTiers().get(0).getBelowManwon()).isEqualTo(5000L);
		assertThat(rates.getBrokerage().getTiers().get(0).getRatePct()).isEqualByComparingTo("0.6");
		assertThat(rates.getBrokerage().getTiers().get(0).getCapManwon()).isEqualTo(25L);
		assertThat(rates.getBrokerage().getTiers().get(2).getCapManwon()).isNull();
		assertThat(rates.getBrokerage().getTiers().get(5).getBelowManwon()).isNull();
		assertThat(rates.getBrokerage().getTiers().get(5).getRatePct()).isEqualByComparingTo("0.7");
	}

	@Test
	void seedValues_shouldPassStoreConsistencyCheck() throws IOException {
		HouseBudgetPolicy policy = HouseBudgetPolicy.builder()
			.ltvFirstTime(new BigDecimal("0.700")).ltvNonFirst(new BigDecimal("0.400"))
			.priceCaps(new PriceCapListConverter().convertToEntityAttribute(jsonLiterals().get(0)))
			.costRates(new CostRatesConverter().convertToEntityAttribute(jsonLiterals().get(1)))
			.build();

		assertThat(HouseBudgetPolicyStore.findConsistencyProblem(policy)).isNull();
	}

	@Test
	void converterRoundTrip_shouldKeepEqualValues() throws IOException {
		CostRatesConverter converter = new CostRatesConverter();
		CostRates parsed = converter.convertToEntityAttribute(jsonLiterals().get(1));

		assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(parsed))).isEqualTo(parsed);
	}
}
