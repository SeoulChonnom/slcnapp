package com.seoulchonnom.aggregate.house.stat;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.store.projection.OutlierCandidatePdo;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

class OutlierMarkerTest {
	private static final LocalDate BASE = LocalDate.of(2026, 1, 10);

	private OutlierCandidatePdo trade(String id, LocalDate date, long amount) {
		return new OutlierCandidatePdo(id, "C1", AreaBucket.B59, date, amount);
	}

	private OutlierCandidatePdo trade(String id, String complexId, AreaBucket bucket, LocalDate date, long amount) {
		return new OutlierCandidatePdo(id, complexId, bucket, date, amount);
	}

	/** 같은 날 금액들. 첫 거래가 id "t0". */
	private List<OutlierCandidatePdo> sameDay(long... amounts) {
		List<OutlierCandidatePdo> list = new ArrayList<>();
		for (int i = 0; i < amounts.length; i++) {
			list.add(trade("t" + i, BASE, amounts[i]));
		}
		return list;
	}

	@Test
	void findOutlierIds_shouldNotJudgeWhenWindowHasFourTrades() {
		// 100,000이 중앙값 대비 크게 벗어나도 창 안에 4건뿐이라 판정하지 않는다.
		List<OutlierCandidatePdo> trades = sameDay(50000, 50000, 50000, 100000);

		assertThat(OutlierMarker.findOutlierIds(trades)).isEmpty();
	}

	@Test
	void findOutlierIds_shouldJudgeWhenWindowHasFiveTrades() {
		List<OutlierCandidatePdo> trades = sameDay(50000, 50000, 50000, 50000, 100000);

		assertThat(OutlierMarker.findOutlierIds(trades)).containsExactly("t4");
	}

	@Test
	void findOutlierIds_shouldTreatExactly40PercentAsNormal() {
		// 중앙값 50,000, 70,000은 정확히 +40%. 경계는 이상치가 아니다.
		List<OutlierCandidatePdo> trades = sameDay(50000, 50000, 50000, 50000, 70000);

		assertThat(OutlierMarker.findOutlierIds(trades)).isEmpty();
	}

	@Test
	void findOutlierIds_shouldMarkJustOver40Percent() {
		List<OutlierCandidatePdo> trades = sameDay(50000, 50000, 50000, 50000, 70001);

		assertThat(OutlierMarker.findOutlierIds(trades)).containsExactly("t4");
	}

	@Test
	void findOutlierIds_shouldMarkLowSideToo() {
		// 중앙값 50,000, 29,999는 −40.002%.
		List<OutlierCandidatePdo> trades = sameDay(50000, 50000, 50000, 50000, 29999);

		assertThat(OutlierMarker.findOutlierIds(trades)).containsExactly("t4");
	}

	@Test
	void findOutlierIds_shouldUseAverageOfMiddleTwoForEvenWindow() {
		// 6건: 정렬하면 40,000 40,000 50,000 60,000 60,000 84,000. 중앙값 = (50,000+60,000)/2 = 55,000.
		// 84,000은 +52.7%라 이상치, 77,000은 정확히 +40%라 정상이어야 한다. 아래는 84,000 경우.
		List<OutlierCandidatePdo> trades = sameDay(40000, 40000, 50000, 60000, 60000, 84000);

		assertThat(OutlierMarker.findOutlierIds(trades)).containsExactly("t5");
	}

	@Test
	void findOutlierIds_shouldTreatEvenWindowBoundaryAsNormal() {
		// 중앙값 (50,000+60,000)/2 = 55,000, 77,000은 정확히 +40%.
		List<OutlierCandidatePdo> trades = sameDay(40000, 40000, 50000, 60000, 60000, 77000);

		assertThat(OutlierMarker.findOutlierIds(trades)).isEmpty();
	}

	@Test
	void findOutlierIds_shouldNotMarkNormalDealsOfRisingMarket() {
		// 18개월에 걸쳐 월 1건씩 50,000 → 85,000으로 오른다(총 +70%).
		// 앞쪽 6개월 창만 쓰면 맨 끝 거래가 이상치로 찍히지만, 앞뒤 6개월 창에서는 모두 정상이다.
		List<OutlierCandidatePdo> trades = new ArrayList<>();
		for (int month = 0; month < 18; month++) {
			trades.add(trade("t" + month, BASE.plusMonths(month), 50000 + month * 2059L));
		}

		assertThat(OutlierMarker.findOutlierIds(trades)).isEmpty();
	}

	@Test
	void findOutlierIds_shouldIgnoreTradesOutsideSixMonthWindow() {
		// 7개월 떨어진 거래는 창에 들어오지 않는다. 가운데 거래 하나만 비싸게 팔렸을 때 양쪽 창 모두 5건 이상이어야 판정된다.
		List<OutlierCandidatePdo> trades = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			trades.add(trade("old" + i, BASE.minusMonths(7), 50000));
		}
		trades.add(trade("high", BASE, 100000));
		// 창(2025-07-10 ~ 2026-07-10)에 high 하나뿐이라 창 건수 1 < 5.

		assertThat(OutlierMarker.findOutlierIds(trades)).isEmpty();
	}

	@Test
	void findOutlierIds_shouldIncludeWindowEndsInclusively() {
		// 기준 거래 BASE의 창은 [BASE−6개월, BASE+6개월]. 양 끝날짜 거래가 창에 포함되어 5건이 채워진다.
		List<OutlierCandidatePdo> trades = new ArrayList<>();
		trades.add(trade("low-end", BASE.minusMonths(6), 50000));
		trades.add(trade("a", BASE, 50000));
		trades.add(trade("b", BASE, 50000));
		trades.add(trade("high-end", BASE.plusMonths(6), 50000));
		trades.add(trade("spike", BASE, 100000));

		assertThat(OutlierMarker.findOutlierIds(trades)).contains("spike");
	}

	@Test
	void findOutlierIds_shouldJudgeEachComplexAndBucketSeparately() {
		List<OutlierCandidatePdo> trades = new ArrayList<>();
		// 단지 C1/B59: 5건 안에 이상치 1건
		for (int i = 0; i < 4; i++) {
			trades.add(trade("a" + i, "C1", AreaBucket.B59, BASE, 50000));
		}
		trades.add(trade("spike", "C1", AreaBucket.B59, BASE, 100000));
		// 같은 단지 B84는 가격대가 다르다. 3건뿐이라 판정 대상이 아니다.
		for (int i = 0; i < 3; i++) {
			trades.add(trade("b" + i, "C1", AreaBucket.B84, BASE, 90000));
		}
		// 다른 단지 C2/B59의 100,000은 정상 가격대다.
		for (int i = 0; i < 5; i++) {
			trades.add(trade("c" + i, "C2", AreaBucket.B59, BASE, 100000));
		}

		assertThat(OutlierMarker.findOutlierIds(trades)).containsExactly("spike");
	}

	@Test
	void findOutlierIds_shouldNotDependOnInputOrder() {
		List<OutlierCandidatePdo> trades = new ArrayList<>(
			sameDay(50000, 50000, 50000, 50000, 100000));
		Collections.reverse(trades);

		assertThat(OutlierMarker.findOutlierIds(trades)).containsExactly("t4");
	}

	@Test
	void findOutlierIds_shouldReturnEmptyForNoTrades() {
		assertThat(OutlierMarker.findOutlierIds(List.of())).isEmpty();
	}
}
