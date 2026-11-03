package com.seoulchonnom.aggregate.house.stat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.seoulchonnom.aggregate.house.store.projection.OutlierCandidatePdo;

/**
 * 이상치 판정. (단지, 면적 구간)별로 거래마다 앞뒤 6개월(양 끝 포함, 자기 자신 포함) 창의 중앙값을 구하고,
 * 창 안 건수가 5건 이상이며 |금액 − 중앙값| / 중앙값 > 40%면 이상치다. 정확히 40%는 정상이다.
 * 창을 앞뒤로 잡는 이유는 상승기·하락기의 정상 거래가 한쪽 끝에서 이상치로 찍히지 않게 하려는 것이다.
 * 순수 Java다. PostgreSQL은 percentile_cont를 윈도 함수로 쓸 수 없어 SQL로 만들지 않는다.
 */
public final class OutlierMarker {
	static final int WINDOW_MONTHS = 6;
	static final int MIN_WINDOW_SIZE = 5;
	/** 중앙값 대비 허용 편차 40%. 정수 비교(|a − m| × 100 > m × 40)로 부동소수점 오차를 피한다. */
	static final long THRESHOLD_PERCENT = 40;

	private OutlierMarker() {
	}

	/**
	 * @return 이상치로 판정한 거래 id. 입력 순서와 무관하다.
	 */
	public static List<String> findOutlierIds(List<OutlierCandidatePdo> candidates) {
		Map<String, List<OutlierCandidatePdo>> groups = new LinkedHashMap<>();
		for (OutlierCandidatePdo candidate : candidates) {
			groups.computeIfAbsent(candidate.complexId() + "|" + candidate.areaBucket(), key -> new ArrayList<>())
				.add(candidate);
		}
		List<String> outlierIds = new ArrayList<>();
		for (List<OutlierCandidatePdo> group : groups.values()) {
			markGroup(group, outlierIds);
		}
		return outlierIds;
	}

	private static void markGroup(List<OutlierCandidatePdo> group, List<String> outlierIds) {
		if (group.size() < MIN_WINDOW_SIZE) {
			return;
		}
		List<OutlierCandidatePdo> sorted = new ArrayList<>(group);
		sorted.sort(Comparator.comparing(OutlierCandidatePdo::dealDate));
		int size = sorted.size();
		// minusMonths/plusMonths는 날짜에 대해 단조 증가라 두 포인터가 뒤로 가지 않는다.
		int lo = 0;
		int hi = 0;
		for (int i = 0; i < size; i++) {
			LocalDate dealDate = sorted.get(i).dealDate();
			LocalDate from = dealDate.minusMonths(WINDOW_MONTHS);
			LocalDate to = dealDate.plusMonths(WINDOW_MONTHS);
			while (sorted.get(lo).dealDate().isBefore(from)) {
				lo++;
			}
			while (hi < size && !sorted.get(hi).dealDate().isAfter(to)) {
				hi++;
			}
			int windowSize = hi - lo;
			if (windowSize < MIN_WINDOW_SIZE) {
				continue;
			}
			long[] amounts = new long[windowSize];
			for (int k = 0; k < windowSize; k++) {
				amounts[k] = sorted.get(lo + k).amountManwon();
			}
			Arrays.sort(amounts);
			long amount = sorted.get(i).amountManwon();
			if (isOutlier(amount, amounts)) {
				outlierIds.add(sorted.get(i).id());
			}
		}
	}

	/**
	 * 중앙값 m이 짝수 개일 때 두 값의 평균(반 단위)이므로 양변에 2를 곱해 정수로 비교한다.
	 * |a − m| × 100 > m × 40  ⇔  |2a − 2m| × 100 > 2m × 40
	 */
	private static boolean isOutlier(long amount, long[] sortedAmounts) {
		int n = sortedAmounts.length;
		long twiceMedian = n % 2 == 1 ? 2 * sortedAmounts[n / 2] : sortedAmounts[n / 2 - 1] + sortedAmounts[n / 2];
		if (twiceMedian <= 0) {
			return false;
		}
		long twiceDiff = Math.abs(2 * amount - twiceMedian);
		return twiceDiff * 100 > twiceMedian * THRESHOLD_PERCENT;
	}
}
