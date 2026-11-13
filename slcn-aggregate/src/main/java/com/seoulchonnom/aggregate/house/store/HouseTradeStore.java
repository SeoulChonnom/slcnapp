package com.seoulchonnom.aggregate.house.store;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.projection.OutlierCandidatePdo;
import com.seoulchonnom.aggregate.house.store.repository.HouseTradeRepository;
import com.seoulchonnom.spec.house.entity.HouseTrade;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HouseTradeStore {
	private static final int OUTLIER_UPDATE_CHUNK = 1000;

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

	/**
	 * 이상치 판정 대상(거래일이 since 이후인 B59/B84 유효 거래).
	 */
	public List<OutlierCandidatePdo> findOutlierCandidates(LocalDate since) {
		return houseTradeRepository.findOutlierCandidates(since);
	}

	/**
	 * 이상치 표시를 since 이후 거래에 대해 다시 정한다: 전부 풀고, 주어진 id만 찍는다. 호출하는 쪽의 트랜잭션 안에서 부른다.
	 * id가 비면 찍는 UPDATE는 실행하지 않는다. 1,000개씩 나눠 보낸다.
	 *
	 * @return 새로 찍은 이상치 수
	 */
	public int replaceOutliers(LocalDate since, List<String> outlierIds) {
		houseTradeRepository.clearOutliers(since);
		int marked = 0;
		for (int from = 0; from < outlierIds.size(); from += OUTLIER_UPDATE_CHUNK) {
			List<String> chunk = outlierIds.subList(from, Math.min(from + OUTLIER_UPDATE_CHUNK, outlierIds.size()));
			marked += houseTradeRepository.markOutliers(String.join(",", chunk));
		}
		return marked;
	}
}
