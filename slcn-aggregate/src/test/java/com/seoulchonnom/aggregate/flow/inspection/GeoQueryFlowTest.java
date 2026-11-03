package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.geo.AddressCandidate;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressMapper;
import com.seoulchonnom.aggregate.inspection.geo.AddressSearchResult;
import com.seoulchonnom.aggregate.inspection.geo.CoordKey;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkToWgs84Converter;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.CoordKeySdo;
import com.seoulchonnom.spec.inspection.facade.sdo.CurrentLocationSdo;
import com.seoulchonnom.spec.inspection.facade.sdo.GeoPointRdo;

class GeoQueryFlowTest {
	private final AddressGateway gateway = mock(AddressGateway.class);
	private final GeoQueryFlow flow = new GeoQueryFlow(gateway, new AddressMapper(),
		new UtmkToWgs84Converter());

	@Test
	void searchAddresses_shouldTrimKeywordAndMapCandidates() {
		CoordKey key = new CoordKey("1", "2", "0", "135", "0");
		when(gateway.search("성수동", 2, 5)).thenReturn(new AddressSearchResult(7,
			List.of(new AddressCandidate("도로명", "지번", "트리마제", "bd1", "04773", key))));

		AddressSearchRdo rdo = flow.searchAddresses("  성수동 ", 2, 5);

		assertThat(rdo.getTotalCount()).isEqualTo(7);
		assertThat(rdo.getItems()).hasSize(1);
		assertThat(rdo.getItems().get(0).getBuildingName()).isEqualTo("트리마제");
		assertThat(rdo.getItems().get(0).getBdMgtSn()).isEqualTo("bd1");
		assertThat(rdo.getItems().get(0).getCoordKey().getRnMgtSn()).isEqualTo("2");
		assertThat(rdo.getItems().get(0).getCoordKey().getBuldMnnm()).isEqualTo("135");
	}

	@Test
	void searchAddresses_shouldRejectInvalidInputWithoutCallingGateway() {
		assertValidationFailure(null, 1, 10);
		assertValidationFailure("   ", 1, 10);
		assertValidationFailure("성수동", 0, 10);
		assertValidationFailure("성수동", 1, 0);
		assertValidationFailure("성수동", 1, 21);
		verifyNoInteractions(gateway);
	}

	@Test
	void searchAddresses_shouldAcceptBoundarySize() {
		when(gateway.search("성수동", 1, 20)).thenReturn(new AddressSearchResult(0, List.of()));

		assertThat(flow.searchAddresses("성수동", 1, 20).getItems()).isEmpty();
	}

	@Test
	void searchAddresses_shouldPropagateGatewayFailure() {
		when(gateway.search(any(), anyInt(), anyInt())).thenThrow(AddressLookupUnavailableException.upstreamFailed());

		assertThatThrownBy(() -> flow.searchAddresses("성수동", 1, 10))
			.isInstanceOf(AddressLookupUnavailableException.class);
	}

	private void assertValidationFailure(String keyword, int page, int size) {
		assertThatThrownBy(() -> flow.searchAddresses(keyword, page, size)).isInstanceOfSatisfying(
			BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	private static CurrentLocationSdo location(String admCd, String rnMgtSn, String udrtYn, String buldMnnm,
		String buldSlno) {
		return new CurrentLocationSdo(new CoordKeySdo(admCd, rnMgtSn, udrtYn, buldMnnm, buldSlno));
	}

	@Test
	void getCurrentLocation_shouldConvertEntranceToWgs84WithoutRounding() {
		UtmkPoint entrance = new UtmkPoint(960000.0, 1950000.0);
		when(gateway.findEntrance(new CoordKey("1171010200", "117103123001", "0", "1", "0"))).thenReturn(entrance);

		GeoPointRdo rdo = flow.getCurrentLocation(location("1171010200", "117103123001", "0", "1", "0"));

		var expected = new UtmkToWgs84Converter().convert(entrance);
		assertThat(rdo.getLatitude()).isEqualTo(expected.latitude());
		assertThat(rdo.getLongitude()).isEqualTo(expected.longitude());
		assertThat(rdo.getLatitude()).isBetween(37.0, 38.0);
		assertThat(rdo.getLongitude()).isBetween(126.0, 128.0);
	}

	@Test
	void getCurrentLocation_shouldRejectMissingOrBlankCoordKeyWithoutCallingGateway() {
		assertCurrentLocationValidation(null);
		assertCurrentLocationValidation(new CurrentLocationSdo());
		assertCurrentLocationValidation(location(null, "2", "0", "1", "0"));
		assertCurrentLocationValidation(location("1", " ", "0", "1", "0"));
		assertCurrentLocationValidation(location("1", "2", "", "1", "0"));
		assertCurrentLocationValidation(location("1", "2", "0", null, "0"));
		assertCurrentLocationValidation(location("1", "2", "0", "1", "  "));
		verifyNoInteractions(gateway);
	}

	@Test
	void getCurrentLocation_shouldPropagateGatewayFailure() {
		when(gateway.findEntrance(any())).thenThrow(AddressLookupUnavailableException.upstreamFailed());

		assertThatThrownBy(() -> flow.getCurrentLocation(location("1", "2", "0", "1", "0")))
			.isInstanceOfSatisfying(AddressLookupUnavailableException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LOOKUP_FAILED));
	}

	/**
	 * 현재 위치는 저장하지 않는다는 약속(설계 04 4절)을 구조로 지킨다: 생성자 의존성이 게이트웨이, 매퍼, 변환기뿐이고
	 * 저장소류(Store, Repository)가 없다.
	 */
	@Test
	void flow_shouldDependOnNoStore() {
		var types = java.util.Arrays.stream(GeoQueryFlow.class.getDeclaredFields())
			.filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
			.map(f -> f.getType().getSimpleName())
			.toList();

		assertThat(types).containsExactlyInAnyOrder("AddressGateway", "AddressMapper", "UtmkToWgs84Converter");
	}

	private void assertCurrentLocationValidation(CurrentLocationSdo request) {
		assertThatThrownBy(() -> flow.getCurrentLocation(request)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}
}
