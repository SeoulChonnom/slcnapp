package com.seoulchonnom.aggregate.house.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.seoulchonnom.aggregate.house.stat.AreaStatBuilder;
import com.seoulchonnom.aggregate.house.stat.HouseStatWindows;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.aggregate.house.store.HouseTradeStore;
import com.seoulchonnom.aggregate.house.store.projection.OutlierCandidatePdo;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

class HouseStatLogicTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
	private static final LocalDate DEAL = LocalDate.of(2026, 9, 1);

	private final HouseTradeStore tradeStore = mock(HouseTradeStore.class);
	private final HouseStatStore statStore = mock(HouseStatStore.class);
	private final HouseStatLogic logic = new HouseStatLogic(tradeStore, new AreaStatBuilder(statStore));

	@Test
	void rebuild_shouldReplaceOutliersBeforeBuildingStats() {
		List<OutlierCandidatePdo> candidates = List.of(
			new OutlierCandidatePdo("t0", "C1", AreaBucket.B59, DEAL, 50000),
			new OutlierCandidatePdo("t1", "C1", AreaBucket.B59, DEAL, 50000),
			new OutlierCandidatePdo("t2", "C1", AreaBucket.B59, DEAL, 50000),
			new OutlierCandidatePdo("t3", "C1", AreaBucket.B59, DEAL, 50000),
			new OutlierCandidatePdo("t4", "C1", AreaBucket.B59, DEAL, 100000));
		HouseStatWindows windows = HouseStatWindows.of(TODAY);
		when(tradeStore.findOutlierCandidates(windows.outlierSince())).thenReturn(candidates);
		when(tradeStore.replaceOutliers(eq(windows.outlierSince()), any())).thenReturn(1);
		when(statStore.rebuildAreaStats(windows)).thenReturn(7);

		StatRebuildResult result = logic.rebuild(TODAY);

		InOrder inOrder = inOrder(tradeStore, statStore);
		inOrder.verify(tradeStore).findOutlierCandidates(windows.outlierSince());
		inOrder.verify(tradeStore).replaceOutliers(windows.outlierSince(), List.of("t4"));
		inOrder.verify(statStore).rebuildAreaStats(windows);
		assertThat(result).isEqualTo(new StatRebuildResult(5, 1, 7));
	}

	@Test
	void rebuild_shouldStillClearOldOutliersAndBuildStatsWhenNoOutlier() {
		HouseStatWindows windows = HouseStatWindows.of(TODAY);
		when(tradeStore.findOutlierCandidates(any())).thenReturn(List.of());

		logic.rebuild(TODAY);

		// 이전에 찍힌 이상치를 풀어야 하므로 빈 목록으로도 교체를 부른다.
		verify(tradeStore).replaceOutliers(windows.outlierSince(), List.of());
		verify(statStore).rebuildAreaStats(windows);
	}
}
