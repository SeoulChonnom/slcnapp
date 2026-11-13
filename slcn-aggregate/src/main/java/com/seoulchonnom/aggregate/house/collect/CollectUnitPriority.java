package com.seoulchonnom.aggregate.house.collect;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.seoulchonnom.spec.house.entity.HouseCollectUnit;
import com.seoulchonnom.spec.house.entity.vo.CollectUnitStatus;

/**
 * 실거래 수집 순서. 한 실행 안에서 앞에서부터 예산이 남는 동안 처리한다.
 *
 * 1. 최근 3개월(이번 달, -1, -2) 단위 전부. 신고 기한(30일) 때문에 최근 월은 계속 늘어나므로 매일 다시 받는다.
 * 2. 이전 실행에서 실패한 단위, 연속 실패가 적은 것부터
 * 3. 한 번도 받지 않은 단위(백필), 최근 월부터 과거로
 * 4. 매월 1일에만: 4~12개월째 단위 중 이번 달 1일 이전에 받은 것(늦게 들어온 해제 신고 반영)
 *
 * 4번 범위는 이번 달을 1개월째로 세어 3~12개월 전(offset -3 ~ -12)이다. 1번이 offset 0~-2를 덮으므로
 * 사이에 빠지는 월이 없다. 같은 우선순위 안에서는 계약년월 내림차순, 구 코드 오름차순이다.
 */
public final class CollectUnitPriority {
	private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
	private static final Comparator<HouseCollectUnit> RECENT_FIRST = Comparator
		.comparing(HouseCollectUnit::getDealYm, Comparator.reverseOrder())
		.thenComparing(HouseCollectUnit::getLawdCd);

	private CollectUnitPriority() {
	}

	/**
	 * 수집 대상 월 범위(40개월). 3년 전 3개월 창을 덮는다. 범위 밖으로 밀려난 옛 단위는 지우지 않고 대상에서만 뺀다.
	 */
	public static List<YearMonth> targetMonths(LocalDate today) {
		YearMonth current = YearMonth.from(today);
		List<YearMonth> months = new ArrayList<>();
		for (int offset = 39; offset >= 0; offset--) {
			months.add(current.minusMonths(offset));
		}
		return months;
	}

	public static String format(YearMonth ym) {
		return ym.format(YM);
	}

	public static YearMonth parse(String ym) {
		return YearMonth.parse(ym, YM);
	}

	public static List<HouseCollectUnit> order(List<HouseCollectUnit> units, LocalDate today) {
		YearMonth current = YearMonth.from(today);
		String recentFrom = format(current.minusMonths(2));
		String windowFrom = format(current.minusMonths(39));
		String windowTo = format(current);

		List<HouseCollectUnit> inWindow = units.stream()
			.filter(unit -> unit.getDealYm().compareTo(windowFrom) >= 0 && unit.getDealYm().compareTo(windowTo) <= 0)
			.toList();

		Set<HouseCollectUnit> ordered = new LinkedHashSet<>();
		inWindow.stream().filter(unit -> unit.getDealYm().compareTo(recentFrom) >= 0).sorted(RECENT_FIRST)
			.forEach(ordered::add);
		inWindow.stream().filter(unit -> unit.getStatus() == CollectUnitStatus.FAILED)
			.sorted(Comparator.comparingInt(HouseCollectUnit::getAttempts).thenComparing(RECENT_FIRST))
			.forEach(ordered::add);
		inWindow.stream().filter(unit -> unit.getStatus() == CollectUnitStatus.NEVER).sorted(RECENT_FIRST)
			.forEach(ordered::add);
		if (today.getDayOfMonth() == 1) {
			String refreshFrom = format(current.minusMonths(12));
			String refreshTo = format(current.minusMonths(3));
			inWindow.stream()
				.filter(unit -> unit.getDealYm().compareTo(refreshFrom) >= 0
					&& unit.getDealYm().compareTo(refreshTo) <= 0)
				.filter(unit -> unit.getLastSuccessDate() == null || unit.getLastSuccessDate().isBefore(today))
				.sorted(RECENT_FIRST)
				.forEach(ordered::add);
		}
		return List.copyOf(ordered);
	}
}
