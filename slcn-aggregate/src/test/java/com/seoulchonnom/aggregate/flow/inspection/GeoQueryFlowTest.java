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
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;

class GeoQueryFlowTest {
	private final AddressGateway gateway = mock(AddressGateway.class);
	private final GeoQueryFlow flow = new GeoQueryFlow(gateway, new AddressMapper());

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
}
