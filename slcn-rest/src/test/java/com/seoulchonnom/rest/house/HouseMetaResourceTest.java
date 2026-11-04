package com.seoulchonnom.rest.house;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

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
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapperImpl;

class HouseMetaResourceTest {
	private HouseCollectUnitStore collectUnitStore;
	private HouseBatchRunStore batchRunStore;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		collectUnitStore = mock(HouseCollectUnitStore.class);
		batchRunStore = mock(HouseBatchRunStore.class);
		Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		HouseQueryFlow flow = new HouseQueryFlow(mock(HouseBudgetPolicyStore.class), new HouseBudgetPolicyMapperImpl(),
			mock(HouseComplexStore.class), mock(HouseStatStore.class), collectUnitStore, batchRunStore, clock);
		mockMvc = MockMvcBuilders.standaloneSetup(new HouseMetaResource(flow))
			.setControllerAdvice(new CommonExceptionHandler()).build();
	}

	@Test
	void getMeta_shouldRenderDates() throws Exception {
		when(collectUnitStore.findDataAsOf(LocalDate.of(2026, 11, 3), 25)).thenReturn(LocalDate.of(2026, 10, 20));
		when(batchRunStore.findLastSuccessRunDate(BatchStep.STATS_BUILD)).thenReturn(Optional.of(LocalDate.of(2026, 10, 20)));

		mockMvc.perform(get("/house/meta"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dataAsOf").value("2026-10-20"))
			.andExpect(jsonPath("$.statsComputedDate").value("2026-10-20"));
	}

	@Test
	void getMeta_shouldRenderNullsAsNullKeys() throws Exception {
		when(batchRunStore.findLastSuccessRunDate(any())).thenReturn(Optional.empty());

		mockMvc.perform(get("/house/meta"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasKey("dataAsOf")))
			.andExpect(jsonPath("$", hasKey("statsComputedDate")))
			.andExpect(jsonPath("$.dataAsOf").value(nullValue()))
			.andExpect(jsonPath("$.statsComputedDate").value(nullValue()));
	}
}
