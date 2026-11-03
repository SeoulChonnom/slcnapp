package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidPropertyLocationException;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressMapper;
import com.seoulchonnom.aggregate.inspection.geo.CoordKey;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkToWgs84Converter;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;
import com.seoulchonnom.spec.inspection.facade.sdo.CoordKeySdo;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyLocationInputSdo;

class PropertyLocationResolverTest {
	private final AddressGateway addressGateway = mock(AddressGateway.class);
	private final UtmkToWgs84Converter converter = new UtmkToWgs84Converter();
	private final PropertyLocationResolver resolver = new PropertyLocationResolver(addressGateway,
		new AddressMapper(), converter);

	private static final PropertyLocation STORED = new PropertyLocation("OLD-1", "기존 도로명주소", 37.5, 127.0,
		960000.0, 1950000.0);

	private static CoordKeySdo coordKey() {
		return new CoordKeySdo("1171010200", "117103123001", "0", "1", "0");
	}

	@Test
	void resolve_shouldRemoveLocationWhenRequestIsNull() {
		assertThat(resolver.resolve(STORED, null)).isNull();
		verifyNoInteractions(addressGateway);
	}

	@Test
	void resolve_shouldRejectBlankBdMgtSn() {
		assertThatThrownBy(() -> resolver.resolve(STORED, new PropertyLocationInputSdo(" ", "주소", coordKey())))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verifyNoInteractions(addressGateway);
	}

	@Test
	void resolve_shouldKeepStoredLocationWithoutLookupWhenBdMgtSnMatches() {
		PropertyLocation result = resolver.resolve(STORED, new PropertyLocationInputSdo("OLD-1", "바뀐 문자열", null));

		assertThat(result).isSameAs(STORED);
		verifyNoInteractions(addressGateway);
	}

	@Test
	void resolve_shouldLookUpAndConvertWhenBdMgtSnDiffers() {
		when(addressGateway.findEntrance(any())).thenReturn(new UtmkPoint(953898.449, 1952035.979));

		PropertyLocation result = resolver.resolve(STORED,
			new PropertyLocationInputSdo("NEW-2", "서울 중구 세종대로 110", coordKey()));

		assertThat(result.getBdMgtSn()).isEqualTo("NEW-2");
		assertThat(result.getRoadAddress()).isEqualTo("서울 중구 세종대로 110");
		assertThat(result.getEntX()).isEqualTo(953898.449);
		assertThat(result.getEntY()).isEqualTo(1952035.979);
		assertThat(result.getLatitude()).isCloseTo(37.566535, within(1e-5));
		assertThat(result.getLongitude()).isCloseTo(126.977969, within(1e-5));
		verify(addressGateway).findEntrance(new CoordKey("1171010200", "117103123001", "0", "1", "0"));
	}

	@Test
	void resolve_shouldLookUpWhenNothingIsStored() {
		when(addressGateway.findEntrance(any())).thenReturn(new UtmkPoint(953898.449, 1952035.979));

		PropertyLocation result = resolver.resolve(null, new PropertyLocationInputSdo("NEW-2", null, coordKey()));

		assertThat(result.getBdMgtSn()).isEqualTo("NEW-2");
		assertThat(result.getRoadAddress()).isNull();
	}

	@Test
	void resolve_shouldRejectMissingOrBlankCoordKeyWhenLookupIsNeeded() {
		CoordKeySdo blankField = new CoordKeySdo("1171010200", " ", "0", "1", "0");

		assertThatThrownBy(() -> resolver.resolve(STORED, new PropertyLocationInputSdo("NEW-2", "주소", null)))
			.isInstanceOf(InvalidPropertyLocationException.class);
		assertThatThrownBy(() -> resolver.resolve(null, new PropertyLocationInputSdo("NEW-2", "주소", blankField)))
			.isInstanceOf(InvalidPropertyLocationException.class);
		verifyNoInteractions(addressGateway);
	}

	@Test
	void resolve_shouldPropagateGatewayFailure() {
		when(addressGateway.findEntrance(any())).thenThrow(AddressLookupUnavailableException.upstreamFailed());

		assertThatThrownBy(() -> resolver.resolve(STORED, new PropertyLocationInputSdo("NEW-2", "주소", coordKey())))
			.isInstanceOf(AddressLookupUnavailableException.class);
	}

	@Test
	void resolve_shouldRejectTooLongBdMgtSnAndRoadAddressBeforeLookup() {
		String longBdMgtSn = "B".repeat(27);
		String longRoad = "도".repeat(301);

		assertThatThrownBy(() -> resolver.resolve(STORED, new PropertyLocationInputSdo(longBdMgtSn, "주소", coordKey())))
			.isInstanceOfSatisfying(InvalidPropertyLocationException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		assertThatThrownBy(() -> resolver.resolve(STORED, new PropertyLocationInputSdo("NEW-2", longRoad, coordKey())))
			.isInstanceOfSatisfying(InvalidPropertyLocationException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verifyNoInteractions(addressGateway);
	}

	@Test
	void resolve_shouldAcceptMaxLengthValues() {
		when(addressGateway.findEntrance(any())).thenReturn(new UtmkPoint(953898.449, 1952035.979));

		PropertyLocation result = resolver.resolve(null,
			new PropertyLocationInputSdo("B".repeat(26), "도".repeat(300), coordKey()));

		assertThat(result.getBdMgtSn()).hasSize(26);
		assertThat(result.getRoadAddress()).hasSize(300);
	}
}
