package com.seoulchonnom.aggregate.house.store;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.collect.CollectUnitPriority;
import com.seoulchonnom.aggregate.house.store.mapper.HouseCollectUnitJpoMapper;
import com.seoulchonnom.aggregate.house.store.repository.HouseCollectUnitRepository;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HouseCollectUnitStore {
	private final HouseCollectUnitRepository houseCollectUnitRepository;
	private final HouseCollectUnitJpoMapper houseCollectUnitJpoMapper;

	/**
	 * 없는 (구, 계약년월) 단위만 만든다.
	 *
	 * @return 새로 만든 단위 수
	 */
	public int insertMissing(List<String> lawdCds, List<YearMonth> dealYms, long now) {
		if (lawdCds.isEmpty() || dealYms.isEmpty()) {
			return 0;
		}
		return houseCollectUnitRepository.insertMissing(String.join(",", lawdCds),
			String.join(",", dealYms.stream().map(CollectUnitPriority::format).toList()), now);
	}

	/**
	 * 오늘 수집할 단위를 수집 순서대로. 수집 범위(40개월) 안 단위 최대 1,000행을 읽어 Java에서 정렬한다.
	 */
	public List<HouseCollectUnit> findDueUnits(LocalDate today) {
		List<YearMonth> months = CollectUnitPriority.targetMonths(today);
		List<HouseCollectUnit> window = houseCollectUnitRepository
			.findByDealYmBetween(CollectUnitPriority.format(months.get(0)),
				CollectUnitPriority.format(months.get(months.size() - 1)))
			.stream()
			.map(houseCollectUnitJpoMapper::toDomain)
			.toList();
		return CollectUnitPriority.order(window, today);
	}

	public Optional<HouseCollectUnit> findById(String unitId) {
		return houseCollectUnitRepository.findById(unitId).map(houseCollectUnitJpoMapper::toDomain);
	}

	public HouseCollectUnit save(HouseCollectUnit unit) {
		return houseCollectUnitJpoMapper
			.toDomain(houseCollectUnitRepository.save(houseCollectUnitJpoMapper.toJpo(unit)));
	}

	/**
	 * 데이터 기준일. 이번 달 포함 최근 3개월 단위 전부(구 수 x 3)가 한 번 이상 성공했을 때만 그중 가장 이른 마지막 성공일, 아니면 null.
	 */
	public LocalDate findDataAsOf(LocalDate today, int districtCount) {
		YearMonth thisMonth = YearMonth.from(today);
		Object value = houseCollectUnitRepository.findDataAsOf(districtCount * 3,
			CollectUnitPriority.format(thisMonth), CollectUnitPriority.format(thisMonth.minusMonths(1)),
			CollectUnitPriority.format(thisMonth.minusMonths(2)));
		if (value instanceof LocalDate date) {
			return date;
		}
		if (value instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		if (value instanceof java.util.Date date) {
			return new java.sql.Date(date.getTime()).toLocalDate();
		}
		return null;
	}
}
