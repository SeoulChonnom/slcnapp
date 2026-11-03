package com.seoulchonnom.rest.house;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.flow.house.HouseAdminFlow;
import com.seoulchonnom.aggregate.house.exception.HouseComplexNotFoundException;
import com.seoulchonnom.aggregate.house.logic.HouseComplexLogic;
import com.seoulchonnom.aggregate.house.store.projection.IncompleteComplexPdo;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;
import com.seoulchonnom.spec.house.mapper.HouseAdminMapperImpl;

/**
 * 관리자 권한(401/403)은 boot의 SecurityConfigurationTest가 맡는다. 여기서는 바인딩과 오류 렌더링을 본다.
 */
class HouseAdminResourceTest {
	private HouseComplexLogic complexLogic;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		complexLogic = mock(HouseComplexLogic.class);
		Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		HouseAdminFlow flow = new HouseAdminFlow(complexLogic, new HouseAdminMapperImpl(), clock);
		mockMvc = MockMvcBuilders.standaloneSetup(new HouseAdminResource(flow))
			.setControllerAdvice(new CommonExceptionHandler()).build();
	}

	private HouseComplex complex() {
		HouseComplex complex = HouseComplex.builder().aptSeq("11230-123").lawdCd("11230").umdName("전농동")
			.bonbun("0123").bubun("0004").aptName("전농래미안").geocodeStatus(GeocodeStatus.FAILED)
			.registerStatus(RegisterStatus.DONE).build();
		complex.setId("HOUSE_COMPLEX-0001");
		return complex;
	}

	@Test
	void getIncomplete_shouldRenderSpecShape() throws Exception {
		HouseComplex complex = complex();
		complex.setAddressOverride(new AddressOverride("서울특별시 동대문구 왕산로 1", null, null));
		when(complexLogic.findIncomplete(any(), eq(20), eq(0))).thenReturn(List.of(new IncompleteComplexPdo(complex, 7)));
		when(complexLogic.countIncomplete()).thenReturn(12L);

		mockMvc.perform(get("/house/admin/complexes/incomplete"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(12))
			.andExpect(jsonPath("$.items[0].complexId").value("HOUSE_COMPLEX-0001"))
			.andExpect(jsonPath("$.items[0].aptSeq").value("11230-123"))
			.andExpect(jsonPath("$.items[0].name").value("전농래미안"))
			.andExpect(jsonPath("$.items[0].districtName").value("동대문구"))
			.andExpect(jsonPath("$.items[0].umdName").value("전농동"))
			.andExpect(jsonPath("$.items[0].bonbun").value("123"))
			.andExpect(jsonPath("$.items[0].bubun").value("4"))
			.andExpect(jsonPath("$.items[0].roadAddress").doesNotExist())
			.andExpect(jsonPath("$.items[0].addressOverride.roadAddress").value("서울특별시 동대문구 왕산로 1"))
			.andExpect(jsonPath("$.items[0].geocodeStatus").value("FAILED"))
			.andExpect(jsonPath("$.items[0].registerStatus").value("DONE"))
			.andExpect(jsonPath("$.items[0].trades12m").value(7));
	}

	@Test
	void getIncomplete_shouldBindPageAndSize() throws Exception {
		when(complexLogic.findIncomplete(any(), eq(50), eq(100))).thenReturn(List.of());

		mockMvc.perform(get("/house/admin/complexes/incomplete").param("page", "3").param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void getIncomplete_shouldRender400ForPageZero() throws Exception {
		mockMvc.perform(get("/house/admin/complexes/incomplete").param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void overrideAddress_shouldRender200WithUpdatedRow() throws Exception {
		when(complexLogic.overrideAddress(eq("HOUSE_COMPLEX-0001"), eq(new AddressOverride("서울특별시 동대문구 왕산로 1", "0123", "0004"))))
			.thenReturn(complex());
		when(complexLogic.countTrades12m(eq("HOUSE_COMPLEX-0001"), any())).thenReturn(7L);

		mockMvc.perform(put("/house/admin/complexes/HOUSE_COMPLEX-0001/address-override")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roadAddress\":\"서울특별시 동대문구 왕산로 1\",\"bonbun\":\"123\",\"bubun\":\"4\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.complexId").value("HOUSE_COMPLEX-0001"))
			.andExpect(jsonPath("$.trades12m").value(7));
	}

	@Test
	void overrideAddress_shouldRender404WhenComplexMissing() throws Exception {
		when(complexLogic.overrideAddress(any(), any())).thenThrow(new HouseComplexNotFoundException());

		mockMvc.perform(put("/house/admin/complexes/nope/address-override")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roadAddress\":\"주소\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOUSE_COMPLEX_NOT_FOUND"));
	}

	@Test
	void overrideAddress_shouldRender400WhenNothingSent() throws Exception {
		mockMvc.perform(put("/house/admin/complexes/c/address-override")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verify(complexLogic, never()).overrideAddress(any(), any());
	}

	@Test
	void overrideAddress_shouldRender400ForBubunOnlyAndBadLotNumber() throws Exception {
		mockMvc.perform(put("/house/admin/complexes/c/address-override")
				.contentType(MediaType.APPLICATION_JSON).content("{\"bubun\":\"3\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(put("/house/admin/complexes/c/address-override")
				.contentType(MediaType.APPLICATION_JSON).content("{\"bonbun\":\"12345\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void overrideAddress_shouldRender400ForMalformedBody() throws Exception {
		mockMvc.perform(put("/house/admin/complexes/c/address-override")
				.contentType(MediaType.APPLICATION_JSON).content("not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
	}
}
