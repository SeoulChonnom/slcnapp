package com.seoulchonnom.aggregate.house.stat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.house.store.projection.RankingInputPdo;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.constant.HouseConstant.District;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingBadge;
import com.seoulchonnom.spec.house.entity.vo.RankingType;

/**
 * 랭킹 4종 x 평형 2개를 Java에서 만든다(순수 계산, 저장소를 모른다). 단계적 완화와 동률 규칙을 SQL로 쓰면 읽기 어려워서다.
 * 단지명 비교는 String.compareTo(코드 포인트 순)로 한다. DB 콜레이션에 기대지 않고, 한글 음절은 가나다 순으로 놓여 있다.
 * 마지막은 complex_id로 가른다(동명 단지에서도 순위가 결정적이도록).
 */
@Component
public class RankingBuilder {
	static final int LEADER_MIN_SAMPLE = 3;
	static final int TREND_MIN_SAMPLE = 3;
	static final int TOP_LIMIT = 20;

	private static final List<AreaBucket> AREAS = List.of(AreaBucket.B59, AreaBucket.B84);

	public List<RankingRow> build(List<RankingInputPdo> inputs, HouseStatWindows windows) {
		List<RankingRow> rows = new ArrayList<>();
		for (AreaBucket area : AREAS) {
			List<RankingInputPdo> ofArea = inputs.stream().filter(input -> input.area() == area).toList();
			rows.addAll(leader(ofArea, area, windows));
			rows.addAll(trend(ofArea, area, RankingType.RISE, windows));
			rows.addAll(trend(ofArea, area, RankingType.FALL, windows));
			rows.addAll(volume(ofArea, area, windows));
		}
		return rows;
	}

	/**
	 * 구마다 1행, 항상 25행. 12개월 3건 이상 중 1위 → 1건 이상 중 1위(LOW_SAMPLE) → 거래 없음(NO_TRADE).
	 * rank_no는 평균가 내림차순이고 거래 없음 행이 맨 뒤(그 안에서는 lawd_cd 순)다.
	 */
	private List<RankingRow> leader(List<RankingInputPdo> inputs, AreaBucket area, HouseStatWindows windows) {
		LocalDate from = windows.w12mStart().plusDays(1);
		Map<String, List<RankingInputPdo>> byDistrict = new HashMap<>();
		for (RankingInputPdo input : inputs) {
			if (input.avg12m() != null && input.cnt12m() >= 1) {
				byDistrict.computeIfAbsent(input.lawdCd(), key -> new ArrayList<>()).add(input);
			}
		}
		Comparator<RankingInputPdo> order = Comparator
			.comparing(RankingInputPdo::avg12m, Comparator.reverseOrder())
			.thenComparing(byChangeDesc())
			.thenComparing(byAbsManwonDesc())
			.thenComparing(RankingInputPdo::aptName)
			.thenComparing(RankingInputPdo::complexId);

		List<RankingRow> traded = new ArrayList<>();
		List<RankingRow> noTrade = new ArrayList<>();
		for (District district : HouseConstant.DISTRICTS) {
			List<RankingInputPdo> candidates = byDistrict.getOrDefault(district.lawdCd(), List.of());
			RankingInputPdo top = candidates.stream().filter(input -> input.cnt12m() >= LEADER_MIN_SAMPLE)
				.min(order).orElse(null);
			RankingBadge badge = null;
			if (top == null) {
				top = candidates.stream().min(order).orElse(null);
				badge = RankingBadge.LOW_SAMPLE;
			}
			if (top == null) {
				noTrade.add(new RankingRow(RankingType.LEADER, area, 0, district.lawdCd(), null, null, null, 0,
					RankingBadge.NO_TRADE, from, windows.today()));
			} else {
				traded.add(new RankingRow(RankingType.LEADER, area, 0, district.lawdCd(), top.complexId(),
					BigDecimal.valueOf(top.avg12m()).setScale(2), top.change1yManwon(), top.cnt12m(), badge, from,
					windows.today()));
			}
		}
		traded.sort(Comparator.comparing(RankingRow::value, Comparator.reverseOrder())
			.thenComparing(RankingRow::lawdCd).thenComparing(RankingRow::complexId));
		noTrade.sort(Comparator.comparing(RankingRow::lawdCd));
		List<RankingRow> ordered = new ArrayList<>(traded);
		ordered.addAll(noTrade);
		return numbered(ordered);
	}

	/**
	 * 1년 상승·하락 TOP. 양쪽 3건 이상이고 변동률 부호가 맞는 단지만, 최대 20행. 모자라면 있는 만큼만 낸다.
	 */
	private List<RankingRow> trend(List<RankingInputPdo> inputs, AreaBucket area, RankingType type,
		HouseStatWindows windows) {
		boolean rise = type == RankingType.RISE;
		Predicate<RankingInputPdo> eligible = input -> input.cnt3m() >= TREND_MIN_SAMPLE
			&& input.cnt3m1yAgo() >= TREND_MIN_SAMPLE && input.change1yPct() != null
			&& (rise ? input.change1yPct().signum() > 0 : input.change1yPct().signum() < 0);
		Comparator<RankingInputPdo> byPct = Comparator.comparing(RankingInputPdo::change1yPct);
		Comparator<RankingInputPdo> order = (rise ? byPct.reversed() : byPct)
			.thenComparing(byAbsManwonDesc())
			.thenComparing(RankingInputPdo::aptName)
			.thenComparing(RankingInputPdo::complexId);
		LocalDate from = windows.w3m1yStart().plusDays(1);
		List<RankingRow> rows = inputs.stream().filter(eligible).sorted(order).limit(TOP_LIMIT)
			.map(input -> new RankingRow(type, area, 0, input.lawdCd(), input.complexId(), input.change1yPct(),
				input.change1yManwon(), Math.min(input.cnt3m(), input.cnt3m1yAgo()), null, from, windows.today()))
			.toList();
		return numbered(rows);
	}

	/**
	 * 거래 활발 TOP. 최근 3개월 건수(이상치 포함) 내림차순, 최대 20행.
	 */
	private List<RankingRow> volume(List<RankingInputPdo> inputs, AreaBucket area, HouseStatWindows windows) {
		Comparator<RankingInputPdo> order = Comparator
			.comparingInt(RankingInputPdo::volume3m).reversed()
			.thenComparing(byChangeDesc())
			.thenComparing(byAbsManwonDesc())
			.thenComparing(RankingInputPdo::aptName)
			.thenComparing(RankingInputPdo::complexId);
		LocalDate from = windows.w3mStart().plusDays(1);
		List<RankingRow> rows = inputs.stream().filter(input -> input.volume3m() > 0).sorted(order).limit(TOP_LIMIT)
			.map(input -> new RankingRow(RankingType.VOLUME, area, 0, input.lawdCd(), input.complexId(),
				BigDecimal.valueOf(input.volume3m()).setScale(2), input.change1yManwon(), input.volume3m(), null,
				from, windows.today()))
			.toList();
		return numbered(rows);
	}

	/** 1년 변동률 내림차순, 계산할 수 없는 단지(null)는 뒤로. */
	private static Comparator<RankingInputPdo> byChangeDesc() {
		return Comparator.comparing(RankingInputPdo::change1yPct,
			Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()));
	}

	/** 1년 등락가격 절댓값 내림차순, null은 뒤로. */
	private static Comparator<RankingInputPdo> byAbsManwonDesc() {
		return Comparator.comparing(
			(RankingInputPdo input) -> input.change1yManwon() == null ? null : Math.abs(input.change1yManwon()),
			Comparator.nullsLast(Comparator.<Long>reverseOrder()));
	}

	/** 정렬된 순서대로 1부터 rank_no를 붙인다. */
	private static List<RankingRow> numbered(List<RankingRow> sorted) {
		List<RankingRow> rows = new ArrayList<>(sorted.size());
		for (int i = 0; i < sorted.size(); i++) {
			RankingRow row = sorted.get(i);
			rows.add(new RankingRow(row.type(), row.area(), i + 1, row.lawdCd(), row.complexId(), row.value(),
				row.changeManwon(), row.sampleCount(), row.badge(), row.periodFrom(), row.periodTo()));
		}
		return rows;
	}
}
