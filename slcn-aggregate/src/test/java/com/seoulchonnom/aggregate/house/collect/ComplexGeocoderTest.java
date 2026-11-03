package com.seoulchonnom.aggregate.house.collect;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult.Outcome;
import com.seoulchonnom.aggregate.inspection.exception.AddressCoordinateNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidAddressKeywordException;
import com.seoulchonnom.aggregate.inspection.geo.AddressCandidate;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressSearchResult;
import com.seoulchonnom.aggregate.inspection.geo.CoordKey;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkToWgs84Converter;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;

class ComplexGeocoderTest {
	private static final CoordKey KEY = new CoordKey("1168010300", "116803122001", "0", "307", "0");

	private final AddressGateway gateway = mock(AddressGateway.class);
	private final List<Duration> sleeps = new ArrayList<>();
	private final HouseProperties properties = new HouseProperties();
	private ComplexGeocoder geocoder;

	@BeforeEach
	void setUp() {
		geocoder = new ComplexGeocoder(gateway, new UtmkToWgs84Converter(), sleeps::add, properties);
	}

	private HouseComplex complex() {
		return HouseComplex.builder().aptSeq("11680-283").lawdCd("11680").umdCd("10300").umdName("개포동")
			.bonbun("0652").bubun("0000").aptName("개포우성3차").roadName("개포로").roadBonbun("307").roadBubun("0")
			.build();
	}

	private AddressSearchResult candidate(String roadAddr) {
		return new AddressSearchResult(1, List.of(new AddressCandidate(roadAddr, "서울 강남구 개포동 652", null, "bd",
			"06300", KEY)));
	}

	private static final AddressSearchResult NONE = new AddressSearchResult(0, List.of());

	private CallBudget budget(int limit) {
		return new CallBudget("juso", limit, 0);
	}

	@Test
	void geocode_shouldUseOverrideRoadAddressFirst() {
		HouseComplex complex = complex();
		complex.setAddressOverride(new AddressOverride("서울특별시 강남구 개포로 310", null, null));
		when(gateway.search(any(), eq(1), eq(1))).thenReturn(candidate("서울특별시 강남구 개포로 310"));
		when(gateway.findEntrance(KEY)).thenReturn(new UtmkPoint(953898.449, 1952035.979));
		CallBudget budget = budget(10);

		GeocodeResult result = geocoder.geocode(complex, budget);

		verify(gateway).search("서울특별시 강남구 개포로 310", 1, 1);
		assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
		assertThat(result.roadAddress()).isEqualTo("서울특별시 강남구 개포로 310");
		assertThat(result.latitude()).isBetween(37.5, 37.6);
		assertThat(result.longitude()).isBetween(126.9, 127.1);
		assertThat(budget.used()).isEqualTo(2);
	}

	@Test
	void geocode_shouldTryRoadNameThenFallBackToLotNumber() {
		when(gateway.search(any(), eq(1), eq(1))).thenReturn(NONE).thenReturn(candidate("서울특별시 강남구 개포로 307"));
		when(gateway.findEntrance(KEY)).thenReturn(new UtmkPoint(953898.449, 1952035.979));

		GeocodeResult result = geocoder.geocode(complex(), budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
		var keywords = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(gateway, times(2)).search(keywords.capture(), eq(1), eq(1));
		// 도로명이 먼저, 지번이 다음이다. 부번이 0이면 생략하고 본번의 앞 0을 없앤다.
		assertThat(keywords.getAllValues()).containsExactly("서울특별시 강남구 개포로 307", "서울특별시 강남구 개포동 652");
	}

	@Test
	void geocode_shouldIncludeNonZeroSubNumberInLotKeywordAndRoadKeyword() {
		HouseComplex complex = complex();
		complex.setBonbun("0091");
		complex.setBubun("0016");
		complex.setRoadBubun("5");
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(NONE);

		geocoder.geocode(complex, budget(10));

		verify(gateway).search("서울특별시 강남구 개포로 307-5", 1, 1);
		verify(gateway).search("서울특별시 강남구 개포동 91-16", 1, 1);
	}

	@Test
	void geocode_shouldReturnNotFoundAfterAllKeywordsHaveNoCandidates() {
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(NONE);

		GeocodeResult result = geocoder.geocode(complex(), budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
		verify(gateway, never()).findEntrance(any());
	}

	@Test
	void geocode_shouldTreatMissingCoordinateAsNextKeywordAndNotFoundAtTheEnd() {
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(candidate("서울특별시 강남구 개포로 307"));
		when(gateway.findEntrance(any())).thenThrow(new AddressCoordinateNotFoundException());

		GeocodeResult result = geocoder.geocode(complex(), budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
		verify(gateway, times(2)).findEntrance(any());
	}

	@Test
	void geocode_shouldMoveToNextKeywordWhenKeywordIsRejected() {
		when(gateway.search(eq("서울특별시 강남구 개포로 307"), anyInt(), anyInt()))
			.thenThrow(new InvalidAddressKeywordException("검색어를 상세히 입력하세요."));
		when(gateway.search(eq("서울특별시 강남구 개포동 652"), anyInt(), anyInt()))
			.thenReturn(candidate("서울특별시 강남구 개포로 307"));
		when(gateway.findEntrance(KEY)).thenReturn(new UtmkPoint(953898.449, 1952035.979));

		assertThat(geocoder.geocode(complex(), budget(10)).outcome()).isEqualTo(Outcome.FOUND);
	}

	@Test
	void geocode_shouldReturnUpstreamFailedOnRateLimitAndStillPause() {
		when(gateway.search(any(), anyInt(), anyInt())).thenThrow(AddressLookupUnavailableException.upstreamFailed());

		GeocodeResult result = geocoder.geocode(complex(), budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.UPSTREAM_FAILED);
		assertThat(sleeps).containsExactly(Duration.ofMillis(1000));
	}

	@Test
	void geocode_shouldReturnNotConfiguredWhenKeyIsMissing() {
		when(gateway.search(any(), anyInt(), anyInt())).thenThrow(AddressLookupUnavailableException.notConfigured());

		assertThat(geocoder.geocode(complex(), budget(10)).outcome()).isEqualTo(Outcome.NOT_CONFIGURED);
	}

	@Test
	void geocode_shouldPauseAfterEveryJusoCall() {
		properties.setJusoIntervalMillis(750);
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(candidate("서울특별시 강남구 개포로 307"));
		when(gateway.findEntrance(KEY)).thenReturn(new UtmkPoint(953898.449, 1952035.979));

		geocoder.geocode(complex(), budget(10));

		// 검색 1번 + 좌표 1번
		assertThat(sleeps).containsExactly(Duration.ofMillis(750), Duration.ofMillis(750));
	}

	@Test
	void geocode_shouldNotSleepWhenIntervalIsZero() {
		properties.setJusoIntervalMillis(0);
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(NONE);

		geocoder.geocode(complex(), budget(10));

		assertThat(sleeps).isEmpty();
	}

	@Test
	void geocode_shouldStopWithBudgetExhaustedWhenBudgetEnds() {
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(candidate("서울특별시 강남구 개포로 307"));

		GeocodeResult result = geocoder.geocode(complex(), budget(1));

		assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
		verify(gateway, never()).findEntrance(any());
	}

	@Test
	void geocode_shouldReturnInterruptedWhenSleepIsInterrupted() {
		ComplexGeocoder interrupted = new ComplexGeocoder(gateway, new UtmkToWgs84Converter(), duration -> {
			throw new InterruptedException();
		}, properties);
		when(gateway.search(any(), anyInt(), anyInt())).thenReturn(NONE);

		assertThat(interrupted.geocode(complex(), budget(10)).outcome()).isEqualTo(Outcome.INTERRUPTED);
		assertThat(Thread.interrupted()).isTrue();
	}

	@Test
	void keywords_shouldUseOverrideBonbunForLotKeywordAndSkipZeroLot() {
		HouseComplex complex = complex();
		complex.setRoadName(null);
		complex.setAddressOverride(new AddressOverride(null, "0123", null));

		assertThat(geocoder.keywords(complex)).containsExactly("서울특별시 강남구 개포동 123");

		complex.setBonbun("0000");
		complex.setAddressOverride(null);
		assertThat(geocoder.keywords(complex)).isEmpty();
	}
}
