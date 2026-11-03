package com.seoulchonnom.rest.house;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
import com.seoulchonnom.aggregate.house.store.projection.AreaStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexMapPdo;
import com.seoulchonnom.aggregate.house.store.projection.DistrictCountPdo;
import com.seoulchonnom.aggregate.house.store.projection.DongSummaryPdo;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapperImpl;

class HouseComplexResourceTest {
	private HouseComplexStore complexStore;
	private HouseStatStore statStore;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		complexStore = mock(HouseComplexStore.class);
		statStore = mock(HouseStatStore.class);
		Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		HouseQueryFlow flow = new HouseQueryFlow(mock(HouseBudgetPolicyStore.class), new HouseBudgetPolicyMapperImpl(),
			complexStore, statStore, mock(HouseCollectUnitStore.class), mock(HouseBatchRunStore.class), clock);
		mockMvc = MockMvcBuilders.standaloneSetup(new HouseComplexResource(flow))
			.setControllerAdvice(new CommonExceptionHandler()).build();
	}

	@Test
	void getComplexes_shouldBindParamsAndRenderSpecShape() throws Exception {
		when(complexStore.findMapItems(eq(AreaBucket.B59), any(), eq(300))).thenReturn(List.of(
			new ComplexMapPdo("HOUSE_COMPLEX-0001", "OO아파트", "11230", "OO동", 37.5789, 127.0451, 1999, 380, 45000L, 3, 5),
			new ComplexMapPdo("HOUSE_COMPLEX-0002", "무가격", "11230", "XX동", 37.58, 127.05, null, null, null, null, null)));
		when(complexStore.countMapItems(eq(AreaBucket.B59), any())).thenReturn(34L);

		mockMvc.perform(get("/house/complexes").param("swLat", "37.540").param("swLng", "126.980")
				.param("neLat", "37.620").param("neLng", "127.100").param("area", "B59")
				.param("minPrice", "45872").param("maxPrice", "57340"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(34))
			.andExpect(jsonPath("$.truncated").value(false))
			.andExpect(jsonPath("$.items[0].complexId").value("HOUSE_COMPLEX-0001"))
			.andExpect(jsonPath("$.items[0].name").value("OO아파트"))
			.andExpect(jsonPath("$.items[0].lawdCd").value("11230"))
			.andExpect(jsonPath("$.items[0].districtName").value("동대문구"))
			.andExpect(jsonPath("$.items[0].umdName").value("OO동"))
			.andExpect(jsonPath("$.items[0].latitude").value(37.5789))
			.andExpect(jsonPath("$.items[0].longitude").value(127.0451))
			.andExpect(jsonPath("$.items[0].recentPrice.priceManwon").value(45000))
			.andExpect(jsonPath("$.items[0].recentPrice.windowMonths").value(3))
			.andExpect(jsonPath("$.items[0].recentPrice.sample").value(5))
			.andExpect(jsonPath("$.items[0].buildYear").value(1999))
			.andExpect(jsonPath("$.items[0].households").value(380))
			.andExpect(jsonPath("$.items[1]", hasKey("recentPrice")))
			.andExpect(jsonPath("$.items[1].recentPrice").value(nullValue()));
	}

	@Test
	void getComplexes_shouldReportTruncatedWhenTotalOver300() throws Exception {
		when(complexStore.findMapItems(any(), any(), anyInt())).thenReturn(List.of());
		when(complexStore.countMapItems(any(), any())).thenReturn(301L);

		mockMvc.perform(get("/house/complexes").param("lawdCd", "11230").param("area", "B84"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(301))
			.andExpect(jsonPath("$.truncated").value(true));
	}

	@Test
	void getComplexes_shouldRender400WithValidationFailedCode() throws Exception {
		mockMvc.perform(get("/house/complexes").param("area", "B59"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/house/complexes").param("lawdCd", "11230").param("area", "OTHER"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/house/complexes").param("lawdCd", "11230"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void getComplexes_shouldRender400InvalidParameterForUnknownAreaOrNonNumber() throws Exception {
		mockMvc.perform(get("/house/complexes").param("lawdCd", "11230").param("area", "B60"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		mockMvc.perform(get("/house/complexes").param("swLat", "abc").param("area", "B59"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void getSummary_shouldRouteToSummaryNotToCard() throws Exception {
		when(complexStore.countByDistrict(any(), any(), any())).thenReturn(List.of(new DistrictCountPdo("11305", 12)));

		mockMvc.perform(get("/house/complexes/summary").param("groupBy", "DISTRICT").param("area", "B59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.groupBy").value("DISTRICT"))
			.andExpect(jsonPath("$.items", hasSize(25)))
			.andExpect(jsonPath("$.items[8].code").value("11305"))
			.andExpect(jsonPath("$.items[8].name").value("강북구"))
			.andExpect(jsonPath("$.items[8].count").value(12))
			.andExpect(jsonPath("$.items[8].centerLat").value(37.639339));
		// 카드(/{complexId})로 갔다면 단지 조회가 일어난다.
		verify(complexStore, never()).findById(any());
	}

	@Test
	void getSummary_shouldRenderDongShape() throws Exception {
		when(complexStore.summarizeByDong(eq(AreaBucket.B59), eq(45872L), eq(57340L), anyDouble(), anyDouble(),
			anyDouble(), anyDouble()))
			.thenReturn(List.of(new DongSummaryPdo("11305", "10100", "미아동", 37.6131, 127.0298, 12)));

		mockMvc.perform(get("/house/complexes/summary").param("groupBy", "DONG").param("area", "B59")
				.param("minPrice", "45872").param("maxPrice", "57340").param("swLat", "37.540")
				.param("swLng", "126.980").param("neLat", "37.620").param("neLng", "127.100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.groupBy").value("DONG"))
			.andExpect(jsonPath("$.items[0].code").value("1130510100"))
			.andExpect(jsonPath("$.items[0].name").value("미아동"))
			.andExpect(jsonPath("$.items[0].lawdCd").value("11305"))
			.andExpect(jsonPath("$.items[0].districtName").value("강북구"))
			.andExpect(jsonPath("$.items[0].count").value(12))
			.andExpect(jsonPath("$.items[0].centerLat").value(37.6131))
			.andExpect(jsonPath("$.items[0].centerLng").value(127.0298));
	}

	@Test
	void getSummary_shouldRender400WhenDistrictHasBounds() throws Exception {
		mockMvc.perform(get("/house/complexes/summary").param("groupBy", "DISTRICT").param("area", "B59")
				.param("swLat", "37.5"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void getComplex_shouldRenderCard() throws Exception {
		HouseComplex complex = HouseComplex.builder().aptSeq("s").lawdCd("11230").umdName("OO동").aptName("OO아파트")
			.latitude(37.5789).longitude(127.0451).buildYear(1999).households(380).build();
		complex.setId("HOUSE_COMPLEX-0001");
		when(complexStore.findById("HOUSE_COMPLEX-0001")).thenReturn(Optional.of(complex));
		when(statStore.findAreaStat("HOUSE_COMPLEX-0001", AreaBucket.B59))
			.thenReturn(Optional.of(new AreaStatPdo(45000L, 3, 5)));

		mockMvc.perform(get("/house/complexes/HOUSE_COMPLEX-0001").param("area", "B59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.complexId").value("HOUSE_COMPLEX-0001"))
			.andExpect(jsonPath("$.name").value("OO아파트"))
			.andExpect(jsonPath("$.districtName").value("동대문구"))
			.andExpect(jsonPath("$.umdName").value("OO동"))
			.andExpect(jsonPath("$.latitude").value(37.5789))
			.andExpect(jsonPath("$.recentPrice.priceManwon").value(45000))
			.andExpect(jsonPath("$.recentPrice.windowMonths").value(3))
			.andExpect(jsonPath("$.recentPrice.sample").value(5));
	}

	@Test
	void getComplex_shouldRender404WhenMissing() throws Exception {
		when(complexStore.findById("HOUSE_COMPLEX-9999")).thenReturn(Optional.empty());

		mockMvc.perform(get("/house/complexes/HOUSE_COMPLEX-9999").param("area", "B59"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOUSE_COMPLEX_NOT_FOUND"));
	}

	@Test
	void getComplex_shouldRender400WhenAreaMissing() throws Exception {
		mockMvc.perform(get("/house/complexes/HOUSE_COMPLEX-0001"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
