package com.seoulchonnom.aggregate.house.stat;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.store.projection.RankingInputPdo;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingBadge;
import com.seoulchonnom.spec.house.entity.vo.RankingType;

class RankingBuilderTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
	private static final HouseStatWindows WINDOWS = HouseStatWindows.of(TODAY);

	private final RankingBuilder builder = new RankingBuilder();

	private static RankingInputPdo leaderInput(String id, String lawdCd, String name, int cnt12m, Long avg12m) {
		return new RankingInputPdo(id, AreaBucket.B59, lawdCd, name, cnt12m, avg12m, 0, 0, 0, null, null);
	}

	private static RankingInputPdo trendInput(String id, String name, int cnt3m, int cnt1yAgo, String pct,
		Long manwon) {
		return new RankingInputPdo(id, AreaBucket.B59, "11680", name, 0, null, cnt3m, cnt1yAgo, 0,
			pct == null ? null : new BigDecimal(pct), manwon);
	}

	private static RankingInputPdo volumeInput(String id, String name, int volume, String pct, Long manwon) {
		return new RankingInputPdo(id, AreaBucket.B59, "11680", name, 0, null, 0, 0, volume,
			pct == null ? null : new BigDecimal(pct), manwon);
	}

	private List<RankingRow> rows(List<RankingInputPdo> inputs, RankingType type, AreaBucket area) {
		return builder.build(inputs, WINDOWS).stream().filter(row -> row.type() == type && row.area() == area)
			.toList();
	}

	@Test
	void build_shouldAlwaysMake25LeaderRowsPerAreaAndNoOtherAreaLeak() {
		List<RankingRow> leaders = rows(List.of(leaderInput("C1", "11680", "A", 5, 100000L)), RankingType.LEADER,
			AreaBucket.B84);

		assertThat(leaders).hasSize(25).allMatch(row -> row.badge() == RankingBadge.NO_TRADE);
		assertThat(rows(List.of(leaderInput("C1", "11680", "A", 5, 100000L)), RankingType.LEADER, AreaBucket.B59))
			.hasSize(25);
	}

	@Test
	void leader_shouldRelaxInThreeStagesAndPutNoTradeLast() {
		List<RankingInputPdo> inputs = List.of(
			// 강남구: 3건 이상 단지가 있으면 평균가가 더 높아도 1건짜리는 뽑지 않는다.
			leaderInput("C1", "11680", "가", 3, 100000L), leaderInput("C2", "11680", "나", 1, 300000L),
			// 서초구: 3건 이상이 없어서 1건 이상 중 1위, LOW_SAMPLE.
			leaderInput("C3", "11650", "다", 2, 150000L), leaderInput("C4", "11650", "라", 1, 160000L),
			// 송파구: 3건 이상, 낮은 값.
			leaderInput("C5", "11710", "마", 4, 90000L));

		List<RankingRow> leaders = rows(inputs, RankingType.LEADER, AreaBucket.B59);

		assertThat(leaders).hasSize(25);
		assertThat(leaders.get(0).complexId()).isEqualTo("C4");
		assertThat(leaders.get(0).badge()).isEqualTo(RankingBadge.LOW_SAMPLE);
		assertThat(leaders.get(0).sampleCount()).isEqualTo(1);
		assertThat(leaders.get(0).value()).isEqualByComparingTo("160000");
		assertThat(leaders.get(1).complexId()).isEqualTo("C1");
		assertThat(leaders.get(1).badge()).isNull();
		assertThat(leaders.get(2).complexId()).isEqualTo("C5");
		assertThat(leaders.subList(3, 25)).allMatch(row -> row.badge() == RankingBadge.NO_TRADE
			&& row.complexId() == null && row.value() == null && row.sampleCount() == 0);
		assertThat(leaders.subList(3, 25)).extracting(RankingRow::lawdCd).isSorted();
		assertThat(leaders).extracting(RankingRow::rankNo)
			.containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 25).boxed().toList());
		assertThat(leaders.get(0).periodFrom()).isEqualTo(LocalDate.of(2025, 10, 21));
		assertThat(leaders.get(0).periodTo()).isEqualTo(TODAY);
	}

	@Test
	void leader_shouldBreakAvgTieByChangePctThenAbsChangeThenNameThenId() {
		List<RankingInputPdo> inputs = List.of(
			new RankingInputPdo("C1", AreaBucket.B59, "11680", "가", 3, 100000L, 0, 0, 0, null, null),
			new RankingInputPdo("C2", AreaBucket.B59, "11680", "나", 3, 100000L, 0, 0, 0, new BigDecimal("1.00"), 1000L),
			new RankingInputPdo("C3", AreaBucket.B59, "11680", "다", 3, 100000L, 0, 0, 0, new BigDecimal("2.00"), 500L),
			new RankingInputPdo("C4", AreaBucket.B59, "11680", "라", 3, 100000L, 0, 0, 0, new BigDecimal("2.00"), -900L));

		RankingRow gangnam = rows(inputs, RankingType.LEADER, AreaBucket.B59).get(0);

		// 변동률 2.00 두 곳 중 |등락가격|이 큰 C4.
		assertThat(gangnam.complexId()).isEqualTo("C4");
		assertThat(gangnam.changeManwon()).isEqualTo(-900L);
	}

	@Test
	void rise_shouldFilterBySampleAndSignAndSortDescending() {
		List<RankingInputPdo> inputs = List.of(
			trendInput("C1", "가", 3, 3, "5.00", 1000L),
			trendInput("C2", "나", 6, 4, "12.40", 26000L),
			trendInput("C3", "다", 2, 5, "30.00", 9000L),
			trendInput("C4", "라", 5, 2, "30.00", 9000L),
			trendInput("C5", "마", 5, 5, "-3.00", -300L),
			trendInput("C6", "바", 5, 5, "0.00", 0L),
			trendInput("C7", "사", 5, 5, null, null));

		List<RankingRow> rise = rows(inputs, RankingType.RISE, AreaBucket.B59);

		assertThat(rise).extracting(RankingRow::complexId).containsExactly("C2", "C1");
		assertThat(rise).extracting(RankingRow::rankNo).containsExactly(1, 2);
		assertThat(rise.get(0).sampleCount()).isEqualTo(4);
		assertThat(rise.get(0).value()).isEqualByComparingTo("12.40");
		assertThat(rise.get(0).changeManwon()).isEqualTo(26000L);
		assertThat(rise.get(0).badge()).isNull();
		assertThat(rise.get(0).periodFrom()).isEqualTo(LocalDate.of(2025, 7, 21));
		assertThat(rise.get(0).periodTo()).isEqualTo(TODAY);
	}

	@Test
	void rise_shouldKeepAtMost20RowsAndNotFillWithOppositeSide() {
		List<RankingInputPdo> inputs = new ArrayList<>();
		for (int i = 1; i <= 25; i++) {
			inputs.add(trendInput(String.format("U%02d", i), "상승" + i, 3, 3, i + ".00", i * 100L));
		}
		inputs.add(trendInput("D1", "하락", 3, 3, "-50.00", -5000L));

		assertThat(rows(inputs, RankingType.RISE, AreaBucket.B59)).hasSize(20)
			.allMatch(row -> row.value().signum() > 0);
		assertThat(rows(inputs.subList(0, 5), RankingType.RISE, AreaBucket.B59)).hasSize(5);
		assertThat(rows(inputs.subList(0, 5), RankingType.FALL, AreaBucket.B59)).isEmpty();
	}

	@Test
	void fall_shouldSortMostNegativeFirst() {
		List<RankingInputPdo> inputs = List.of(
			trendInput("C1", "가", 3, 3, "-2.00", -200L),
			trendInput("C2", "나", 3, 3, "-15.50", -9000L),
			trendInput("C3", "다", 3, 3, "-7.00", -4000L),
			trendInput("C4", "라", 3, 3, "4.00", 400L));

		List<RankingRow> fall = rows(inputs, RankingType.FALL, AreaBucket.B59);

		assertThat(fall).extracting(RankingRow::complexId).containsExactly("C2", "C3", "C1");
		assertThat(fall.get(0).value()).isEqualByComparingTo("-15.50");
	}

	@Test
	void rise_shouldBreakTiesByAbsChangeThenNameCodePointThenId() {
		List<RankingInputPdo> inputs = List.of(
			trendInput("C1", "나", 3, 3, "10.00", 500L),
			trendInput("C2", "가", 3, 3, "10.00", 500L),
			trendInput("C3", "다", 3, 3, "10.00", 800L),
			trendInput("C4", "가", 3, 3, "10.00", 500L));

		List<RankingRow> rise = rows(inputs, RankingType.RISE, AreaBucket.B59);

		// 등락가격 큰 C3 → 같으면 단지명 → 동명이면 complex_id.
		assertThat(rise).extracting(RankingRow::complexId).containsExactly("C3", "C2", "C4", "C1");
	}

	@Test
	void volume_shouldSortByCountThenChangeWithNullsLastAndSkipZero() {
		List<RankingInputPdo> inputs = List.of(
			volumeInput("C1", "가", 5, null, null),
			volumeInput("C2", "나", 5, "1.00", 100L),
			volumeInput("C3", "다", 9, null, null),
			volumeInput("C4", "라", 5, "1.00", -900L),
			volumeInput("C5", "마", 0, "50.00", 9999L));

		List<RankingRow> volume = rows(inputs, RankingType.VOLUME, AreaBucket.B59);

		// 건수 9 → 건수 5 중 변동률 1.00 두 곳(|등락| 큰 C4 먼저) → 변동률 null.
		assertThat(volume).extracting(RankingRow::complexId).containsExactly("C3", "C4", "C2", "C1");
		assertThat(volume.get(0).value()).isEqualByComparingTo("9");
		assertThat(volume.get(0).sampleCount()).isEqualTo(9);
		assertThat(volume.get(0).periodFrom()).isEqualTo(LocalDate.of(2026, 7, 21));
	}

	@Test
	void sort_shouldCompareNamesByCodePointNotLocale() {
		List<RankingInputPdo> inputs = List.of(
			trendInput("C1", "나나", 3, 3, "10.00", 500L),
			trendInput("C2", "Zeta", 3, 3, "10.00", 500L),
			trendInput("C3", "가가", 3, 3, "10.00", 500L),
			trendInput("C4", "apple", 3, 3, "10.00", 500L));

		// 코드 포인트 순: 대문자 < 소문자 < 한글 음절(가나다 순).
		assertThat(rows(inputs, RankingType.RISE, AreaBucket.B59)).extracting(RankingRow::complexId)
			.containsExactly("C2", "C4", "C3", "C1");
	}
}
