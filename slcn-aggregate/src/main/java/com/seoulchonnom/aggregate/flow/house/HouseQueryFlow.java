package com.seoulchonnom.aggregate.flow.house;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.exception.HouseComplexNotFoundException;
import com.seoulchonnom.aggregate.house.store.ComplexMapFilter;
import com.seoulchonnom.aggregate.house.store.HouseBatchRunStore;
import com.seoulchonnom.aggregate.house.store.HouseBudgetPolicyStore;
import com.seoulchonnom.aggregate.house.store.HouseCollectUnitStore;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.aggregate.house.store.projection.AreaStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexMapPdo;
import com.seoulchonnom.aggregate.house.store.projection.DistrictCountPdo;
import com.seoulchonnom.aggregate.house.store.projection.DongSummaryPdo;
import com.seoulchonnom.aggregate.house.store.projection.RankingRowPdo;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.constant.HouseConstant.District;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.ComplexGroupBy;
import com.seoulchonnom.spec.house.entity.vo.RankingType;
import com.seoulchonnom.spec.house.facade.sdo.BudgetPolicyRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexCardRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexMapItemRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexMapListRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSearchItemRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSearchRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSummaryItemRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSummaryRdo;
import com.seoulchonnom.spec.house.facade.sdo.HouseMetaRdo;
import com.seoulchonnom.spec.house.facade.sdo.PeriodRdo;
import com.seoulchonnom.spec.house.facade.sdo.RankingItemRdo;
import com.seoulchonnom.spec.house.facade.sdo.RankingListRdo;
import com.seoulchonnom.spec.house.facade.sdo.RecentPriceRdo;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapper;

import lombok.RequiredArgsConstructor;

/**
 * 내집찾기 조회 API. 입력 검증을 첫머리에서 하고(BusinessException VALIDATION_FAILED), 조회는 Store에 맡긴다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseQueryFlow {
	static final int MAP_LIMIT = 300;
	static final int SEARCH_DEFAULT_SIZE = 10;
	static final int SEARCH_MAX_SIZE = 20;
	/** 지도 영역 한 변의 최대 크기(도). 위도·경도 모두 같은 값으로 잰다. */
	static final BigDecimal MAP_MAX_SPAN = new BigDecimal("0.2");
	static final BigDecimal DONG_MAX_SPAN = new BigDecimal("1.0");

	private final HouseBudgetPolicyStore houseBudgetPolicyStore;
	private final HouseBudgetPolicyMapper houseBudgetPolicyMapper;
	private final HouseComplexStore houseComplexStore;
	private final HouseStatStore houseStatStore;
	private final HouseCollectUnitStore houseCollectUnitStore;
	private final HouseBatchRunStore houseBatchRunStore;
	private final Clock houseClock;

	public BudgetPolicyRdo getBudgetPolicy() {
		return houseBudgetPolicyMapper
			.toBudgetPolicyRdo(houseBudgetPolicyStore.findEffective(LocalDate.now(houseClock)));
	}

	public HouseMetaRdo getMeta() {
		LocalDate today = LocalDate.now(houseClock);
		LocalDate dataAsOf = houseCollectUnitStore.findDataAsOf(today, HouseConstant.DISTRICTS.size());
		LocalDate statsComputedDate = houseBatchRunStore.findLastSuccessRunDate(BatchStep.STATS_BUILD).orElse(null);
		return new HouseMetaRdo(dataAsOf == null ? null : dataAsOf.toString(),
			statsComputedDate == null ? null : statsComputedDate.toString());
	}

	public ComplexMapListRdo getComplexes(Double swLat, Double swLng, Double neLat, Double neLng, String lawdCd,
		AreaBucket area, Long minPrice, Long maxPrice) {
		validateArea(area);
		boolean hasBounds = swLat != null || swLng != null || neLat != null || neLng != null;
		boolean hasDistrict = lawdCd != null;
		if (hasBounds == hasDistrict) {
			throw invalid("영역(swLat, swLng, neLat, neLng)과 lawdCd 중 하나만 보내야 합니다.");
		}
		validatePrice(minPrice, maxPrice);
		ComplexMapFilter filter;
		if (hasBounds) {
			validateBounds(swLat, swLng, neLat, neLng, MAP_MAX_SPAN);
			filter = ComplexMapFilter.ofBounds(swLat, swLng, neLat, neLng, minPrice, maxPrice);
		} else {
			if (!HouseConstant.isDistrict(lawdCd)) {
				throw invalid("lawdCd는 서울 25개 구 코드여야 합니다.");
			}
			filter = ComplexMapFilter.ofDistrict(lawdCd, minPrice, maxPrice);
		}
		List<ComplexMapItemRdo> items = houseComplexStore.findMapItems(area, filter, MAP_LIMIT).stream()
			.map(HouseQueryFlow::toMapItem)
			.toList();
		long total = houseComplexStore.countMapItems(area, filter);
		return new ComplexMapListRdo(total, total > MAP_LIMIT, items);
	}

	public ComplexSummaryRdo getSummary(ComplexGroupBy groupBy, AreaBucket area, Long minPrice, Long maxPrice,
		Double swLat, Double swLng, Double neLat, Double neLng) {
		if (groupBy == null) {
			throw invalid("groupBy는 DISTRICT 또는 DONG이어야 합니다.");
		}
		validateArea(area);
		validatePrice(minPrice, maxPrice);
		boolean hasBounds = swLat != null || swLng != null || neLat != null || neLng != null;
		if (groupBy == ComplexGroupBy.DISTRICT) {
			if (hasBounds) {
				throw invalid("groupBy=DISTRICT는 영역 파라미터를 받지 않습니다.");
			}
			return new ComplexSummaryRdo(groupBy, districtItems(area, minPrice, maxPrice));
		}
		validateBounds(swLat, swLng, neLat, neLng, DONG_MAX_SPAN);
		return new ComplexSummaryRdo(groupBy, dongItems(area, minPrice, maxPrice, swLat, swLng, neLat, neLng));
	}

	/**
	 * 단지명 자동완성. keyword는 trim 후 2자 이상, size는 1~20(기본 10).
	 */
	public ComplexSearchRdo searchComplexes(String keyword, Integer size) {
		String trimmed = keyword == null ? "" : keyword.strip();
		if (trimmed.length() < 2) {
			throw invalid("keyword는 2자 이상이어야 합니다.");
		}
		int limit = size == null ? SEARCH_DEFAULT_SIZE : size;
		if (limit < 1 || limit > SEARCH_MAX_SIZE) {
			throw invalid("size는 1 이상 " + SEARCH_MAX_SIZE + " 이하여야 합니다.");
		}
		List<ComplexSearchItemRdo> items = houseComplexStore.searchByName(trimmed, limit).stream()
			.map(complex -> new ComplexSearchItemRdo(complex.getId(), complex.getAptName(),
				HouseConstant.districtName(complex.getLawdCd()), complex.getUmdName(), complex.resolveBuildYear()))
			.toList();
		return new ComplexSearchRdo(items);
	}

	public ComplexCardRdo getComplex(String complexId, AreaBucket area) {
		validateArea(area);
		HouseComplex complex = houseComplexStore.findById(complexId)
			.orElseThrow(HouseComplexNotFoundException::new);
		RecentPriceRdo recentPrice = houseStatStore.findAreaStat(complexId, area)
			.map(HouseQueryFlow::toRecentPrice)
			.orElse(null);
		return new ComplexCardRdo(complex.getId(), complex.getAptName(),
			HouseConstant.districtName(complex.getLawdCd()), complex.getUmdName(), complex.getLatitude(),
			complex.getLongitude(), complex.resolveBuildYear(), complex.getHouseholds(), recentPrice);
	}

	/**
	 * 미리 계산된 랭킹. 랭킹 행이 없으면 items는 빈 배열, period는 null이다(통계가 한 번도 안 돌았음).
	 */
	public RankingListRdo getRankings(RankingType type, AreaBucket area) {
		if (type == null) {
			throw invalid("type은 LEADER, RISE, FALL, VOLUME 중 하나여야 합니다.");
		}
		validateArea(area);
		List<RankingRowPdo> rows = houseStatStore.findRankings(type, area);
		if (rows.isEmpty()) {
			return new RankingListRdo(type, area, null, List.of());
		}
		RankingRowPdo first = rows.get(0);
		return new RankingListRdo(type, area,
			new PeriodRdo(first.periodFrom().toString(), first.periodTo().toString()),
			rows.stream().map(HouseQueryFlow::toRankingItem).toList());
	}

	private static RankingItemRdo toRankingItem(RankingRowPdo row) {
		return new RankingItemRdo(row.rankNo(), row.complexId(), row.aptName(), row.lawdCd(),
			HouseConstant.districtName(row.lawdCd()), row.latitude(), row.longitude(), row.buildYear(),
			row.avgArea12m(), row.households(), row.value(), row.changeManwon(), row.sampleCount(), row.badge());
	}

	/**
	 * 25개 구를 항상 모두 낸다. 좌표 있는 단지가 없는 구는 0건이고, 중심은 구별 고정 좌표다.
	 */
	private List<ComplexSummaryItemRdo> districtItems(AreaBucket area, Long minPrice, Long maxPrice) {
		Map<String, Long> counts = new HashMap<>();
		for (DistrictCountPdo row : houseComplexStore.countByDistrict(area, minPrice, maxPrice)) {
			counts.put(row.lawdCd(), row.count());
		}
		List<ComplexSummaryItemRdo> items = new ArrayList<>();
		for (District district : HouseConstant.DISTRICTS) {
			items.add(new ComplexSummaryItemRdo(district.lawdCd(), district.name(), district.lawdCd(), district.name(),
				counts.getOrDefault(district.lawdCd(), 0L), district.centerLat(), district.centerLng()));
		}
		return items;
	}

	private List<ComplexSummaryItemRdo> dongItems(AreaBucket area, Long minPrice, Long maxPrice, double swLat,
		double swLng, double neLat, double neLng) {
		List<ComplexSummaryItemRdo> items = new ArrayList<>();
		for (DongSummaryPdo row : houseComplexStore.summarizeByDong(area, minPrice, maxPrice, swLat, swLng, neLat,
			neLng)) {
			if (row.umdCd() == null) {
				continue;
			}
			items.add(new ComplexSummaryItemRdo(row.lawdCd() + row.umdCd(), row.umdName(), row.lawdCd(),
				HouseConstant.districtName(row.lawdCd()), row.count(), row.centerLat(), row.centerLng()));
		}
		return items;
	}

	private static ComplexMapItemRdo toMapItem(ComplexMapPdo row) {
		RecentPriceRdo recentPrice = row.recentPriceManwon() == null ? null
			: new RecentPriceRdo(row.recentPriceManwon(), row.recentWindowMonths(), row.recentSample());
		return new ComplexMapItemRdo(row.complexId(), row.aptName(), row.lawdCd(),
			HouseConstant.districtName(row.lawdCd()), row.umdName(), row.latitude(), row.longitude(), recentPrice,
			row.buildYear(), row.households());
	}

	private static RecentPriceRdo toRecentPrice(AreaStatPdo stat) {
		if (stat.recentPriceManwon() == null) {
			return null;
		}
		return new RecentPriceRdo(stat.recentPriceManwon(), stat.recentWindowMonths(), stat.recentSample());
	}

	/** B59/B84만 허용한다. OTHER는 통계가 없는 구간이다. */
	private static void validateArea(AreaBucket area) {
		if (area == null || area == AreaBucket.OTHER) {
			throw invalid("area는 B59 또는 B84여야 합니다.");
		}
	}

	private static void validatePrice(Long minPrice, Long maxPrice) {
		if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)) {
			throw invalid("minPrice, maxPrice는 0 이상이어야 합니다.");
		}
		if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
			throw invalid("minPrice는 maxPrice보다 클 수 없습니다.");
		}
	}

	/**
	 * 4개 모두 있어야 하고, 남서 < 북동이며 각 변이 maxSpan도 이하여야 한다.
	 * 변의 길이는 BigDecimal로 잰다. double 뺄셈은 37.62 − 37.42 = 0.20000000000000284처럼 경계값을 넘겨 보이게 한다.
	 */
	private static void validateBounds(Double swLat, Double swLng, Double neLat, Double neLng, BigDecimal maxSpan) {
		if (swLat == null || swLng == null || neLat == null || neLng == null) {
			throw invalid("영역(swLat, swLng, neLat, neLng) 4개가 모두 필요합니다.");
		}
		if (!Double.isFinite(swLat) || !Double.isFinite(swLng) || !Double.isFinite(neLat) || !Double.isFinite(neLng)) {
			throw invalid("영역 좌표가 올바르지 않습니다.");
		}
		if (swLat >= neLat || swLng >= neLng) {
			throw invalid("swLat < neLat, swLng < neLng 이어야 합니다.");
		}
		if (span(swLat, neLat).compareTo(maxSpan) > 0 || span(swLng, neLng).compareTo(maxSpan) > 0) {
			throw invalid("영역의 한 변은 " + maxSpan.toPlainString() + "도 이하여야 합니다.");
		}
	}

	private static BigDecimal span(double from, double to) {
		return BigDecimal.valueOf(to).subtract(BigDecimal.valueOf(from));
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
	}
}
