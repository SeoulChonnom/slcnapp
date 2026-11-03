package com.seoulchonnom.aggregate.house.store;

import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.repository.HouseTradeRepository;
import com.seoulchonnom.spec.house.entity.HouseTrade;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HouseTradeStore {
	private final HouseTradeRepository houseTradeRepository;

	public enum UpsertResult {
		INSERTED, UPDATED, UNCHANGED
	}

	/**
	 * dedupKey가 같은 거래가 있으면 해제 여부·거래 유형·removed만 바꾸고, 값이 같으면 아무것도 쓰지 않는다.
	 */
	public UpsertResult upsert(HouseTrade trade) {
		var inserted = houseTradeRepository.upsert(UUID.randomUUID().toString(), System.currentTimeMillis(),
			trade.getDedupKey(), trade.getComplexId(), trade.getLawdCd(), trade.getDealYm(), trade.getDealDate(),
			trade.getExclusiveArea(), trade.getAreaBucket().name(), trade.getAmountManwon(), trade.getFloor(),
			trade.getAptDong(), trade.getBuildYear(), trade.getDealingType().name(), trade.isCancelled(),
			trade.getCancelledDate());
		if (inserted.isEmpty()) {
			return UpsertResult.UNCHANGED;
		}
		return Boolean.TRUE.equals(inserted.get(0)) ? UpsertResult.INSERTED : UpsertResult.UPDATED;
	}

	/**
	 * 이번 응답에 없는 거래를 원천 삭제로 표시한다. 키가 없으면 단위 전체가 지워지는 쿼리가 되므로 실행하지 않는다.
	 *
	 * @return 새로 removed가 된 행 수
	 */
	public int markRemovedExcept(String lawdCd, String dealYm, Collection<String> responseKeys) {
		if (responseKeys == null || responseKeys.isEmpty()) {
			return 0;
		}
		return houseTradeRepository.markRemovedExcept(lawdCd, dealYm, String.join(",", responseKeys),
			System.currentTimeMillis());
	}
}
