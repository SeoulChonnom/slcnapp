package com.seoulchonnom.rest.house;

import static org.hamcrest.Matchers.*;
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
import com.seoulchonnom.aggregate.house.store.HouseBatchRunStore;
import com.seoulchonnom.aggregate.house.store.HouseBudgetPolicyStore;
import com.seoulchonnom.aggregate.house.store.HouseCollectUnitStore;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.aggregate.house.store.projection.RankingRowPdo;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingBadge;
import com.seoulchonnom.spec.house.entity.vo.RankingType;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapperImpl;

class HouseRankingResourceTest {
	private HouseStatStore statStore;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		statStore = mock(HouseStatStore.class);
		Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		HouseQueryFlow flow = new HouseQueryFlow(mock(HouseBudgetPolicyStore.class), new HouseBudgetPolicyMapperImpl(),
			mock(HouseComplexStore.class), statStore, mock(HouseCollectUnitStore.class),
			mock(HouseBatchRunStore.class), clock);
		mockMvc = MockMvcBuilders.standaloneSetup(new HouseRankingResource(flow))
			.setControllerAdvice(new CommonExceptionHandler()).build();
	}

	@Test
	void getRankings_shouldBindParamsAndRenderSpecShape() throws Exception {
		LocalDate from = LocalDate.of(2025, 7, 21);
		LocalDate to = LocalDate.of(2026, 10, 20);
		when(statStore.findRankings(RankingType.RISE, AreaBucket.B84)).thenReturn(List.of(
			new RankingRowPdo(1, "C1", "OO아파트", "11680", 37.49, 127.06, 2014, new BigDecimal("84.90"), 1466,
				new BigDecimal("12.40"), 26000L, 6, null, from, to)));

		mockMvc.perform(get("/house/rankings").param("type", "RISE").param("area", "B84"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("RISE"))
			.andExpect(jsonPath("$.area").value("B84"))
			.andExpect(jsonPath("$.period.from").value("2025-07-21"))
			.andExpect(jsonPath("$.period.to").value("2026-10-20"))
			.andExpect(jsonPath("$.items[0].rank").value(1))
			.andExpect(jsonPath("$.items[0].complexId").value("C1"))
			.andExpect(jsonPath("$.items[0].name").value("OO아파트"))
			.andExpect(jsonPath("$.items[0].lawdCd").value("11680"))
			.andExpect(jsonPath("$.items[0].districtName").value("강남구"))
			.andExpect(jsonPath("$.items[0].latitude").value(37.49))
			.andExpect(jsonPath("$.items[0].longitude").value(127.06))
			.andExpect(jsonPath("$.items[0].buildYear").value(2014))
			.andExpect(jsonPath("$.items[0].avgExclusiveArea").value(84.9))
			.andExpect(jsonPath("$.items[0].households").value(1466))
			.andExpect(jsonPath("$.items[0].value").value(12.4))
			.andExpect(jsonPath("$.items[0].changeManwon").value(26000))
			.andExpect(jsonPath("$.items[0].sample").value(6))
			.andExpect(jsonPath("$.items[0]", hasKey("badge")))
			.andExpect(jsonPath("$.items[0].badge").value(nullValue()));
	}

	@Test
	void getRankings_shouldRenderNoTradeRowWithNulls() throws Exception {
		LocalDate from = LocalDate.of(2025, 10, 21);
		LocalDate to = LocalDate.of(2026, 10, 20);
		when(statStore.findRankings(RankingType.LEADER, AreaBucket.B59)).thenReturn(List.of(
			new RankingRowPdo(25, null, null, "11110", null, null, null, null, null, null, null, 0,
				RankingBadge.NO_TRADE, from, to)));

		mockMvc.perform(get("/house/rankings").param("type", "LEADER").param("area", "B59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].badge").value("NO_TRADE"))
			.andExpect(jsonPath("$.items[0].lawdCd").value("11110"))
			.andExpect(jsonPath("$.items[0].districtName").value("종로구"))
			.andExpect(jsonPath("$.items[0]", hasKey("complexId")))
			.andExpect(jsonPath("$.items[0].complexId").value(nullValue()))
			.andExpect(jsonPath("$.items[0].value").value(nullValue()))
			.andExpect(jsonPath("$.items[0].latitude").value(nullValue()));
	}

	@Test
	void getRankings_shouldReturnEmptyItemsAndNullPeriodWhenNoRows() throws Exception {
		when(statStore.findRankings(any(), any())).thenReturn(List.of());

		mockMvc.perform(get("/house/rankings").param("type", "VOLUME").param("area", "B59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$", hasKey("period")))
			.andExpect(jsonPath("$.period").value(nullValue()));
	}

	@Test
	void getRankings_shouldRender400ForMissingOrInvalidParams() throws Exception {
		mockMvc.perform(get("/house/rankings").param("area", "B59"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/house/rankings").param("type", "RISE"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/house/rankings").param("type", "RISE").param("area", "OTHER"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/house/rankings").param("type", "TOP").param("area", "B59"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		verifyNoInteractions(statStore);
	}
}
