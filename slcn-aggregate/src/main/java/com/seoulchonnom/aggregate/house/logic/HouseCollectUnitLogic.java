package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.collect.CollectUnitPriority;
import com.seoulchonnom.aggregate.house.store.HouseCollectUnitStore;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseCollectUnitLogic {
	private final HouseCollectUnitStore houseCollectUnitStore;

	/**
	 * 25개 구 × 최근 40개월 단위를 보장한다. 이미 있는 단위는 건드리지 않는다.
	 *
	 * @return 새로 만든 단위 수
	 */
	@Transactional
	public int prepare(LocalDate today) {
		List<String> lawdCds = HouseConstant.DISTRICTS.stream().map(HouseConstant.District::lawdCd).toList();
		List<YearMonth> months = CollectUnitPriority.targetMonths(today);
		return houseCollectUnitStore.insertMissing(lawdCds, months, System.currentTimeMillis());
	}

	/**
	 * 오늘 수집할 단위를 수집 순서대로 돌려준다.
	 */
	public List<HouseCollectUnit> findDueUnits(LocalDate today) {
		return houseCollectUnitStore.findDueUnits(today);
	}

	/**
	 * 호출이 재시도 후에도 실패했거나 반영 중 오류가 난 단위. 연속 실패 횟수를 올린다.
	 */
	@Transactional
	public void markFailed(String unitId) {
		houseCollectUnitStore.findById(unitId).ifPresent(unit -> {
			unit.markFailed();
			houseCollectUnitStore.save(unit);
		});
	}

	/**
	 * 모든 페이지를 받아 반영한 단위. HouseTradeLogic.applyUnit의 트랜잭션 안에서 불린다.
	 */
	@Transactional
	public void markSucceeded(String unitId, LocalDate today) {
		houseCollectUnitStore.findById(unitId).ifPresent(unit -> {
			unit.markSucceeded(today);
			houseCollectUnitStore.save(unit);
		});
	}
}
