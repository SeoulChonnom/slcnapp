package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.stat.AreaStatBuilder;
import com.seoulchonnom.aggregate.house.stat.HouseStatWindows;
import com.seoulchonnom.aggregate.house.stat.OutlierMarker;
import com.seoulchonnom.aggregate.house.stat.RankingBuilder;
import com.seoulchonnom.aggregate.house.stat.RankingRow;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.aggregate.house.store.HouseTradeStore;
import com.seoulchonnom.aggregate.house.store.projection.OutlierCandidatePdo;

import lombok.RequiredArgsConstructor;

/**
 * 6단계 통계. 한 트랜잭션에서 이상치를 다시 판정하고 통계를 통째로 다시 만든다.
 * 실패하면 전부 롤백되어 이전 통계가 그대로 남고, 커밋 전까지 조회 API는 이전 데이터를 본다(MVCC).
 * 랭킹도 같은 트랜잭션이라 통계와 랭킹이 어긋난 채로 보이지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseStatLogic {
	private final HouseTradeStore houseTradeStore;
	private final AreaStatBuilder areaStatBuilder;
	private final HouseStatStore houseStatStore;
	private final RankingBuilder rankingBuilder;

	@Transactional
	public StatRebuildResult rebuild(LocalDate today) {
		HouseStatWindows windows = HouseStatWindows.of(today);
		List<OutlierCandidatePdo> candidates = houseTradeStore.findOutlierCandidates(windows.outlierSince());
		List<String> outlierIds = OutlierMarker.findOutlierIds(candidates);
		int outliers = houseTradeStore.replaceOutliers(windows.outlierSince(), outlierIds);
		int statRows = areaStatBuilder.rebuild(windows);
		List<RankingRow> rankings = rankingBuilder.build(houseStatStore.findRankingInputs(), windows);
		int rankingRows = houseStatStore.replaceRankings(rankings, today);
		return new StatRebuildResult(candidates.size(), outliers, statRows, rankingRows);
	}
}
