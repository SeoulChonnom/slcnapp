package com.seoulchonnom.rest.inspection;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.flow.inspection.GeoQueryFlow;
import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidAddressKeywordException;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressMapper;
import com.seoulchonnom.aggregate.inspection.geo.AddressCandidate;
import com.seoulchonnom.aggregate.inspection.geo.AddressSearchResult;
import com.seoulchonnom.aggregate.inspection.geo.CoordKey;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;

/**
 * 게이트웨이만 대역으로 두고 Flow까지 실제로 태워, 검증 실패와 외부 오류가 JSON 에러 형태로 내려가는지 본다.
 */
class GeoResourceTest {
	private AddressGateway gateway;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		gateway = mock(AddressGateway.class);
		GeoResource resource = new GeoResource(new GeoQueryFlow(gateway, new AddressMapper()));
		mockMvc = MockMvcBuilders.standaloneSetup(resource).setControllerAdvice(new CommonExceptionHandler()).build();
	}

	@Test
	void searchAddresses_shouldReturnItemsWithCoordKey() throws Exception {
		when(gateway.search("성수동", 1, 10)).thenReturn(new AddressSearchResult(12, List.of(new AddressCandidate(
			"서울특별시 성동구 용답토방길 135", "서울특별시 성동구 성수동1가 685-1", "트리마제", "bd1", "04773",
			new CoordKey("1120011500", "112003115001", "0", "135", "0")))));

		mockMvc.perform(get("/geo/addresses").param("keyword", "성수동"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(12))
			.andExpect(jsonPath("$.items[0].roadAddress").value("서울특별시 성동구 용답토방길 135"))
			.andExpect(jsonPath("$.items[0].jibunAddress").value("서울특별시 성동구 성수동1가 685-1"))
			.andExpect(jsonPath("$.items[0].buildingName").value("트리마제"))
			.andExpect(jsonPath("$.items[0].bdMgtSn").value("bd1"))
			.andExpect(jsonPath("$.items[0].zipNo").value("04773"))
			.andExpect(jsonPath("$.items[0].coordKey.admCd").value("1120011500"))
			.andExpect(jsonPath("$.items[0].coordKey.rnMgtSn").value("112003115001"))
			.andExpect(jsonPath("$.items[0].coordKey.udrtYn").value("0"))
			.andExpect(jsonPath("$.items[0].coordKey.buldMnnm").value("135"))
			.andExpect(jsonPath("$.items[0].coordKey.buldSlno").value("0"));
	}

	@Test
	void searchAddresses_shouldReturnEmptyItems() throws Exception {
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(new AddressSearchResult(0, List.of()));

		mockMvc.perform(get("/geo/addresses").param("keyword", "성수동").param("page", "3").param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(0))
			.andExpect(jsonPath("$.items").isEmpty());
		verify(gateway).search("성수동", 3, 20);
	}

	@Test
	void searchAddresses_shouldReturnValidationFailedForBadInput() throws Exception {
		mockMvc.perform(get("/geo/addresses").param("keyword", "   "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/geo/addresses").param("keyword", "성수동").param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/geo/addresses").param("keyword", "성수동").param("size", "21"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/geo/addresses"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
		verifyNoInteractions(gateway);
	}

	@Test
	void searchAddresses_shouldMapGatewayErrors() throws Exception {
		doThrow(new InvalidAddressKeywordException("검색어는 두글자 이상 입력되어야 합니다.")).when(gateway).search(any(), anyInt(), anyInt());
		mockMvc.perform(get("/geo/addresses").param("keyword", "a"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ADDRESS_KEYWORD"))
			.andExpect(jsonPath("$.title").value("검색어는 두글자 이상 입력되어야 합니다."));

		doThrow(AddressLookupUnavailableException.upstreamFailed()).when(gateway).search(any(), anyInt(), anyInt());
		mockMvc.perform(get("/geo/addresses").param("keyword", "성수동"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("ADDRESS_LOOKUP_FAILED"))
			.andExpect(jsonPath("$.status").value(502));

		doThrow(AddressLookupUnavailableException.notConfigured()).when(gateway).search(any(), anyInt(), anyInt());
		mockMvc.perform(get("/geo/addresses").param("keyword", "성수동"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("ADDRESS_LOOKUP_UNAVAILABLE"));
	}
}
