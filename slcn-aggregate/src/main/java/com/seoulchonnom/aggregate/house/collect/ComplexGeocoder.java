package com.seoulchonnom.aggregate.house.collect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.inspection.exception.AddressCoordinateNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidAddressKeywordException;
import com.seoulchonnom.aggregate.inspection.geo.AddressCandidate;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressSearchResult;
import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkToWgs84Converter;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 단지의 대표 좌표를 행안부로 찾는다. 임장 도메인(inspection)이 쓰는 AddressGateway와 UtmkToWgs84Converter를
 * 그대로 주입받아 같이 쓴다. 패키지를 옮기지는 않았다.
 *
 * 행안부는 쉬지 않고 연달아 부르면 20건 남짓에서 순간 호출 제한 오류를 낸다. 그래서 호출(성공·실패 모두)이 끝날 때마다
 * juso-interval-millis만큼 쉰다. 마지막 호출 뒤의 대기는 낭비지만 상태를 들고 있지 않아도 되는 쪽을 골랐다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexGeocoder {
	private final AddressGateway addressGateway;
	private final UtmkToWgs84Converter utmkToWgs84Converter;
	private final HouseSleeper houseSleeper;
	private final HouseProperties houseProperties;

	/**
	 * 검색어를 순서대로 시도해 첫 후보의 입구 좌표를 돌려준다. 호출마다 예산을 먼저 쓴다.
	 */
	public GeocodeResult geocode(HouseComplex complex, CallBudget budget) {
		for (String keyword : keywords(complex)) {
			if (!budget.consume()) {
				return GeocodeResult.of(GeocodeResult.Outcome.BUDGET_EXHAUSTED);
			}
			AddressSearchResult search;
			try {
				search = addressGateway.search(keyword, 1, 1);
			} catch (InvalidAddressKeywordException e) {
				// 이 검색어만 거절당한 것이다. 다음 검색어로 간다.
				if (!pause()) {
					return GeocodeResult.of(GeocodeResult.Outcome.INTERRUPTED);
				}
				continue;
			} catch (AddressLookupUnavailableException e) {
				return failureAfterPause(e);
			}
			if (!pause()) {
				return GeocodeResult.of(GeocodeResult.Outcome.INTERRUPTED);
			}
			if (search.candidates() == null || search.candidates().isEmpty()) {
				continue;
			}
			AddressCandidate candidate = search.candidates().get(0);

			if (!budget.consume()) {
				return GeocodeResult.of(GeocodeResult.Outcome.BUDGET_EXHAUSTED);
			}
			UtmkPoint entrance;
			try {
				entrance = addressGateway.findEntrance(candidate.coordKey());
			} catch (AddressCoordinateNotFoundException e) {
				// 주소는 있는데 좌표가 없다. 다른 검색어가 다른 후보를 줄 수 있으니 이어서 시도한다.
				if (!pause()) {
					return GeocodeResult.of(GeocodeResult.Outcome.INTERRUPTED);
				}
				continue;
			} catch (AddressLookupUnavailableException e) {
				return failureAfterPause(e);
			}
			if (!pause()) {
				return GeocodeResult.of(GeocodeResult.Outcome.INTERRUPTED);
			}
			GeoPoint point;
			try {
				point = utmkToWgs84Converter.convert(entrance);
			} catch (AddressLookupUnavailableException e) {
				// 국내 범위를 벗어난 좌표처럼 상대가 준 값이 이상한 경우다. 이 후보는 못 쓰므로 다음 검색어로 간다.
				continue;
			}
			return GeocodeResult.found(point.latitude(), point.longitude(), candidate.roadAddress());
		}
		return GeocodeResult.of(GeocodeResult.Outcome.NOT_FOUND);
	}

	/**
	 * 검색어 순서: 관리자 보정 주소, 도로명, 지번. 값이 모자란 검색어는 건너뛴다.
	 */
	List<String> keywords(HouseComplex complex) {
		List<String> keywords = new ArrayList<>();
		AddressOverride override = complex.getAddressOverride();
		if (override != null && hasText(override.getRoadAddress())) {
			keywords.add(override.getRoadAddress().strip());
		}
		String district = HouseConstant.districtName(complex.getLawdCd());
		String prefix = district == null ? "서울특별시" : "서울특별시 " + district;
		if (hasText(complex.getRoadName()) && hasText(complex.getRoadBonbun())) {
			keywords.add(prefix + " " + complex.getRoadName().strip() + " "
				+ number(complex.getRoadBonbun(), complex.getRoadBubun()));
		}
		String bonbun = effectiveBonbun(complex);
		if (hasText(complex.getUmdName()) && hasText(bonbun) && !stripZeros(bonbun).equals("0")) {
			keywords.add(prefix + " " + complex.getUmdName().strip() + " " + number(bonbun, effectiveBubun(complex)));
		}
		return keywords;
	}

	/**
	 * 관리자가 지번을 보정했으면 그 값이 단지 값을 대신한다(건축물대장 조회와 같은 규칙).
	 */
	public static String effectiveBonbun(HouseComplex complex) {
		AddressOverride override = complex.getAddressOverride();
		return override != null && hasText(override.getBonbun()) ? override.getBonbun() : complex.getBonbun();
	}

	public static String effectiveBubun(HouseComplex complex) {
		AddressOverride override = complex.getAddressOverride();
		if (override != null && hasText(override.getBonbun())) {
			// 보정은 통째로 교체이므로 보정에 부번이 없으면 부번 없음(0000)이다.
			return hasText(override.getBubun()) ? override.getBubun() : "0000";
		}
		return complex.getBubun();
	}

	/** "본번[-부번]". 앞 0을 없애고 부번이 없거나 0이면 생략한다. */
	private static String number(String bonbun, String bubun) {
		String main = stripZeros(bonbun);
		String sub = bubun == null ? "0" : stripZeros(bubun);
		return sub.equals("0") ? main : main + "-" + sub;
	}

	private static String stripZeros(String value) {
		String stripped = value.strip().replaceFirst("^0+", "");
		return stripped.isEmpty() ? "0" : stripped;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * 실패한 호출 뒤에도 쉰다. 순간 호출 제한 오류가 바로 이 경로로 오기 때문이다.
	 */
	private GeocodeResult failureAfterPause(AddressLookupUnavailableException e) {
		if (!pause()) {
			return GeocodeResult.of(GeocodeResult.Outcome.INTERRUPTED);
		}
		// 키 미설정·거절은 503, 그 밖의 상대 쪽 실패(순간 호출 제한 포함)는 502다.
		return e.getErrorCode() == ErrorCode.ADDRESS_LOOKUP_UNAVAILABLE
			? GeocodeResult.of(GeocodeResult.Outcome.NOT_CONFIGURED)
			: GeocodeResult.of(GeocodeResult.Outcome.UPSTREAM_FAILED);
	}

	/**
	 * @return 인터럽트 없이 쉬었으면 true
	 */
	private boolean pause() {
		long millis = houseProperties.getJusoIntervalMillis();
		if (millis <= 0) {
			return true;
		}
		try {
			houseSleeper.sleep(Duration.ofMillis(millis));
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
