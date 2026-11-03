package com.seoulchonnom.aggregate.flow.house;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.stat.CompareItem;
import com.seoulchonnom.aggregate.house.stat.CompareItemsParser;
import com.seoulchonnom.aggregate.house.store.HouseBatchRunStore;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseReferenceStore;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.aggregate.house.store.projection.CompareStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexRedevelopmentPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexSchoolPdo;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.SampleStatus;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;
import com.seoulchonnom.spec.house.facade.sdo.AreaInfoRdo;
import com.seoulchonnom.spec.house.facade.sdo.CompareColumnRdo;
import com.seoulchonnom.spec.house.facade.sdo.CompareRdo;
import com.seoulchonnom.spec.house.facade.sdo.FarRdo;
import com.seoulchonnom.spec.house.facade.sdo.PriceChangeRdo;
import com.seoulchonnom.spec.house.facade.sdo.RecentPriceRdo;
import com.seoulchonnom.spec.house.facade.sdo.RedevelopmentRdo;
import com.seoulchonnom.spec.house.facade.sdo.SchoolRdo;

import lombok.RequiredArgsConstructor;

/**
 * 비교표 조립. 단지·통계·학교·정비사업이 서로 다른 저장소라 여기서 모은다. 단지 3개 이하라 모두 일괄 조회 한 번씩이다.
 * 없는 단지는 404가 아니라 missingIds로 알리고, 나머지 열은 그대로 낸다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseCompareQueryFlow {
	private final HouseComplexStore houseComplexStore;
	private final HouseStatStore houseStatStore;
	private final HouseReferenceStore houseReferenceStore;
	private final HouseBatchRunStore houseBatchRunStore;
	private final Clock houseClock;

	public CompareRdo compare(String items) {
		List<CompareItem> requested = CompareItemsParser.parse(items);
		LocalDate today = LocalDate.now(houseClock);

		List<String> requestedIds = requested.stream().map(CompareItem::complexId).distinct().toList();
		Map<String, HouseComplex> complexes = houseComplexStore.findByIds(requestedIds);
		List<String> missingIds = requestedIds.stream().filter(id -> !complexes.containsKey(id)).toList();
		List<String> foundIds = requestedIds.stream().filter(complexes::containsKey).toList();
		if (foundIds.isEmpty()) {
			return new CompareRdo(missingIds, List.of());
		}

		Map<String, CompareStatPdo> stats = new HashMap<>();
		for (CompareStatPdo stat : houseStatStore.findCompareStats(foundIds)) {
			stats.put(statKey(stat.complexId(), stat.areaBucket()), stat);
		}
		Map<String, List<SchoolRdo>> schools = new HashMap<>();
		for (ComplexSchoolPdo school : houseReferenceStore.findComplexSchools(foundIds)) {
			schools.computeIfAbsent(school.complexId(), id -> new ArrayList<>()).add(
				new SchoolRdo(SchoolLevel.valueOf(school.level()), school.schoolName(), school.distanceM()));
		}
		Map<String, List<RedevelopmentRdo>> redevelopments = loadRedevelopments(foundIds);

		List<CompareColumnRdo> columns = new ArrayList<>();
		for (CompareItem item : requested) {
			HouseComplex complex = complexes.get(item.complexId());
			if (complex == null) {
				continue;
			}
			columns.add(toColumn(complex, item.area(), stats.get(statKey(complex.getId(), item.area())),
				schools.getOrDefault(complex.getId(), List.of()),
				redevelopments == null ? null : redevelopments.getOrDefault(complex.getId(), List.of()), today));
		}
		return new CompareRdo(missingIds, columns);
	}

	/**
	 * 정비사업 동기화가 SUCCESS로 끝난 적이 한 번도 없으면 null("정보 없음")이다. 있으면 단지별 목록이고, 없는 단지는 빈 목록이다.
	 * 테이블이 비었는지로 판단하지 않는다. 동기화는 성공했는데 사업이 0건인 경우와 미적재를 구분할 수 없어서다.
	 */
	private Map<String, List<RedevelopmentRdo>> loadRedevelopments(List<String> complexIds) {
		if (houseBatchRunStore.findLastSuccessRunDate(BatchStep.REDEVELOPMENT_SYNC).isEmpty()) {
			return null;
		}
		Map<String, List<RedevelopmentRdo>> byComplex = new HashMap<>();
		for (ComplexRedevelopmentPdo row : houseReferenceStore.findComplexRedevelopments(complexIds)) {
			byComplex.computeIfAbsent(row.complexId(), id -> new ArrayList<>()).add(new RedevelopmentRdo(row.name(),
				row.projectType(), row.stage(), row.distanceM(), row.sourceUrl()));
		}
		return byComplex;
	}

	private static CompareColumnRdo toColumn(HouseComplex complex, AreaBucket area, CompareStatPdo stat,
		List<SchoolRdo> schools, List<RedevelopmentRdo> redevelopments, LocalDate today) {
		Integer buildYear = complex.resolveBuildYear();
		Integer ageYears = buildYear == null ? null : today.getYear() - buildYear;
		RecentPriceRdo recentPrice = stat == null || stat.recentPriceManwon() == null ? null
			: new RecentPriceRdo(stat.recentPriceManwon(), stat.recentWindowMonths(), stat.recentSample());
		FarRdo far = complex.getFarPct() == null ? null : new FarRdo(complex.getFarPct(), complex.getFarSource());
		PriceChangeRdo change1y = stat == null ? null
			: toChange(stat.avg3m1yAgo(), stat.avg3m(), stat.cnt3m1yAgo(), stat.cnt3m(), stat.change1yPct(),
				stat.change1yManwon());
		PriceChangeRdo change3y = stat == null ? null
			: toChange(stat.avg3m3yAgo(), stat.avg3m(), stat.cnt3m3yAgo(), stat.cnt3m(), stat.change3yPct(),
				stat.change3yManwon());
		return new CompareColumnRdo(complex.getId(), complex.getAptName(),
			HouseConstant.districtName(complex.getLawdCd()), complex.getUmdName(), area, recentPrice,
			complex.getHouseholds(), buildYear, ageYears,
			new AreaInfoRdo(stat == null ? null : stat.avgArea12m(), area), far, schools, redevelopments, change1y,
			change3y);
	}

	/**
	 * 과거 3개월 평균 -> 최근 3개월 평균. 표본이 모자라면(어느 한쪽이 2건 미만) 변동률·등락가격은 내리지 않고,
	 * 평균은 건수가 0인 쪽만 비운다. 통계 테이블이 이미 같은 규칙으로 만들지만 응답 규칙이 여기서도 깨지지 않게 한 번 더 막는다.
	 */
	private static PriceChangeRdo toChange(Long fromManwon, Long toManwon, int sampleFrom, int sampleTo,
		java.math.BigDecimal pct, Long changeManwon) {
		SampleStatus status = SampleStatus.of(sampleFrom, sampleTo);
		if (status == SampleStatus.INSUFFICIENT) {
			return new PriceChangeRdo(sampleFrom == 0 ? null : fromManwon, sampleTo == 0 ? null : toManwon, null, null,
				sampleFrom, sampleTo, status);
		}
		return new PriceChangeRdo(fromManwon, toManwon, pct, changeManwon, sampleFrom, sampleTo, status);
	}

	private static String statKey(String complexId, AreaBucket area) {
		return complexId + "|" + area.name();
	}
}
