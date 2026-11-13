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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.flow.house.HouseCompareQueryFlow;
import com.seoulchonnom.aggregate.house.store.HouseBatchRunStore;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseReferenceStore;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.aggregate.house.store.projection.CompareStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexRedevelopmentPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexSchoolPdo;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.FarSource;

class HouseCompareResourceTest {
	private HouseComplexStore complexStore;
	private HouseStatStore statStore;
	private HouseReferenceStore referenceStore;
	private HouseBatchRunStore batchRunStore;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		complexStore = mock(HouseComplexStore.class);
		statStore = mock(HouseStatStore.class);
		referenceStore = mock(HouseReferenceStore.class);
		batchRunStore = mock(HouseBatchRunStore.class);
		Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		HouseCompareQueryFlow flow = new HouseCompareQueryFlow(complexStore, statStore, referenceStore, batchRunStore,
			clock);
		mockMvc = MockMvcBuilders.standaloneSetup(new HouseCompareResource(flow))
			.setControllerAdvice(new CommonExceptionHandler()).build();
		when(batchRunStore.findLastSuccessRunDate(BatchStep.REDEVELOPMENT_SYNC)).thenReturn(Optional.empty());
		when(statStore.findCompareStats(any())).thenReturn(List.of());
		when(referenceStore.findComplexSchools(any())).thenReturn(List.of());
		when(referenceStore.findComplexRedevelopments(any())).thenReturn(List.of());
	}

	private HouseComplex complex(String id) {
		HouseComplex complex = HouseComplex.builder().aptSeq("s-" + id).lawdCd("11230").umdName("OO동")
			.aptName("OO아파트").buildYear(1999).households(380).farPct(new BigDecimal("365.74"))
			.farSource(FarSource.RECAP).build();
		complex.setId(id);
		return complex;
	}

	@Test
	void getCompare_shouldRenderSpecShape() throws Exception {
		when(complexStore.findByIds(any())).thenReturn(Map.of("c1", complex("c1")));
		when(statStore.findCompareStats(any())).thenReturn(List.of(new CompareStatPdo("c1", AreaBucket.B59,
			new BigDecimal("59.80"), 45000L, 3, 5, 45000L, 5, 43000L, 3, new BigDecimal("4.65"), 2000L, 40000L, 2,
			new BigDecimal("12.50"), 5000L)));
		when(referenceStore.findComplexSchools(any()))
			.thenReturn(List.of(new ComplexSchoolPdo("c1", "E", "OO초등학교", 604)));
		when(batchRunStore.findLastSuccessRunDate(BatchStep.REDEVELOPMENT_SYNC))
			.thenReturn(Optional.of(LocalDate.of(2026, 10, 1)));
		when(referenceStore.findComplexRedevelopments(any())).thenReturn(List.of(
			new ComplexRedevelopmentPdo("c1", "OO재정비촉진구역", "재개발", "관리처분인가", 320, "https://x")));

		mockMvc.perform(get("/house/compare").param("items", "c1:B59,c9:B84"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.missingIds", hasSize(1)))
			.andExpect(jsonPath("$.missingIds[0]").value("c9"))
			.andExpect(jsonPath("$.columns", hasSize(1)))
			.andExpect(jsonPath("$.columns[0].complexId").value("c1"))
			.andExpect(jsonPath("$.columns[0].name").value("OO아파트"))
			.andExpect(jsonPath("$.columns[0].districtName").value("동대문구"))
			.andExpect(jsonPath("$.columns[0].umdName").value("OO동"))
			.andExpect(jsonPath("$.columns[0].area").value("B59"))
			.andExpect(jsonPath("$.columns[0].recentPrice.priceManwon").value(45000))
			.andExpect(jsonPath("$.columns[0].recentPrice.windowMonths").value(3))
			.andExpect(jsonPath("$.columns[0].recentPrice.sample").value(5))
			.andExpect(jsonPath("$.columns[0].households").value(380))
			.andExpect(jsonPath("$.columns[0].buildYear").value(1999))
			.andExpect(jsonPath("$.columns[0].ageYears").value(27))
			.andExpect(jsonPath("$.columns[0].areaInfo.avgExclusiveArea").value(59.8))
			.andExpect(jsonPath("$.columns[0].areaInfo.bucket").value("B59"))
			.andExpect(jsonPath("$.columns[0].far.pct").value(365.74))
			.andExpect(jsonPath("$.columns[0].far.source").value("RECAP"))
			.andExpect(jsonPath("$.columns[0].schools[0].level").value("E"))
			.andExpect(jsonPath("$.columns[0].schools[0].name").value("OO초등학교"))
			.andExpect(jsonPath("$.columns[0].schools[0].distanceM").value(604))
			.andExpect(jsonPath("$.columns[0].redevelopments[0].name").value("OO재정비촉진구역"))
			.andExpect(jsonPath("$.columns[0].redevelopments[0].projectType").value("재개발"))
			.andExpect(jsonPath("$.columns[0].redevelopments[0].stage").value("관리처분인가"))
			.andExpect(jsonPath("$.columns[0].redevelopments[0].distanceM").value(320))
			.andExpect(jsonPath("$.columns[0].redevelopments[0].sourceUrl").value("https://x"))
			.andExpect(jsonPath("$.columns[0].change1y.fromManwon").value(43000))
			.andExpect(jsonPath("$.columns[0].change1y.toManwon").value(45000))
			.andExpect(jsonPath("$.columns[0].change1y.pct").value(4.65))
			.andExpect(jsonPath("$.columns[0].change1y.changeManwon").value(2000))
			.andExpect(jsonPath("$.columns[0].change1y.sampleFrom").value(3))
			.andExpect(jsonPath("$.columns[0].change1y.sampleTo").value(5))
			.andExpect(jsonPath("$.columns[0].change1y.sampleStatus").value("OK"))
			.andExpect(jsonPath("$.columns[0].change3y.pct").value(12.5))
			.andExpect(jsonPath("$.columns[0].change3y.sampleStatus").value("LOW"));
	}

	@Test
	void getCompare_shouldRenderNullRedevelopmentsKeyWhenNotLoadedAndEmptyArrayWhenLoaded() throws Exception {
		when(complexStore.findByIds(any())).thenReturn(Map.of("c1", complex("c1")));

		mockMvc.perform(get("/house/compare").param("items", "c1:B59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columns[0]", hasKey("redevelopments")))
			.andExpect(jsonPath("$.columns[0].redevelopments").value(nullValue()))
			.andExpect(jsonPath("$.columns[0].schools", hasSize(0)))
			.andExpect(jsonPath("$.columns[0]", hasKey("change1y")))
			.andExpect(jsonPath("$.columns[0].change1y").value(nullValue()))
			.andExpect(jsonPath("$.columns[0].recentPrice").value(nullValue()));

		when(batchRunStore.findLastSuccessRunDate(BatchStep.REDEVELOPMENT_SYNC))
			.thenReturn(Optional.of(LocalDate.of(2026, 10, 1)));

		mockMvc.perform(get("/house/compare").param("items", "c1:B59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columns[0].redevelopments", hasSize(0)));
	}

	@Test
	void getCompare_shouldRender200WithEmptyColumnsWhenEveryComplexIsMissing() throws Exception {
		when(complexStore.findByIds(any())).thenReturn(Map.of());

		mockMvc.perform(get("/house/compare").param("items", "c9:B59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.missingIds", hasSize(1)))
			.andExpect(jsonPath("$.missingIds[0]").value("c9"))
			.andExpect(jsonPath("$.columns", hasSize(0)));
	}

	@Test
	void getCompare_shouldRender400WithErrorsArrayForInvalidItems() throws Exception {
		mockMvc.perform(get("/house/compare").param("items", "c1:B59,c1:B59,c2:B60,c3"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors", hasSize(3)))
			.andExpect(jsonPath("$.errors[0].field").value("items[1]"))
			.andExpect(jsonPath("$.errors[0].code").value("DUPLICATED"))
			.andExpect(jsonPath("$.errors[1].field").value("items[2]"))
			.andExpect(jsonPath("$.errors[1].code").value("INVALID_AREA"))
			.andExpect(jsonPath("$.errors[2].field").value("items[3]"))
			.andExpect(jsonPath("$.errors[2].code").value("TOO_MANY"));
		verifyNoInteractions(complexStore);
	}

	@Test
	void getCompare_shouldRender400WithFormatErrorWhenItemsMissing() throws Exception {
		mockMvc.perform(get("/house/compare"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("items"))
			.andExpect(jsonPath("$.errors[0].code").value("FORMAT"));
	}
}
