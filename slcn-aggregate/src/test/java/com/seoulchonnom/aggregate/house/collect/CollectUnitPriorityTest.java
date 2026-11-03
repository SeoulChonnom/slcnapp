package com.seoulchonnom.aggregate.house.collect;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.house.entity.HouseCollectUnit;
import com.seoulchonnom.spec.house.entity.vo.CollectUnitStatus;

class CollectUnitPriorityTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

	private HouseCollectUnit unit(String lawdCd, String ym, CollectUnitStatus status, int attempts,
		LocalDate lastSuccess) {
		return HouseCollectUnit.builder().lawdCd(lawdCd).dealYm(ym).status(status).attempts(attempts)
			.lastSuccessDate(lastSuccess).build();
	}

	private List<String> labels(List<HouseCollectUnit> units) {
		return units.stream().map(unit -> unit.getLawdCd() + "/" + unit.getDealYm()).toList();
	}

	@Test
	void targetMonths_shouldCoverFortyMonthsEndingThisMonth() {
		var months = CollectUnitPriority.targetMonths(TODAY);

		assertThat(months).hasSize(40);
		assertThat(CollectUnitPriority.format(months.get(0))).isEqualTo("202307");
		assertThat(CollectUnitPriority.format(months.get(39))).isEqualTo("202610");
	}

	@Test
	void order_shouldPutRecentThreeMonthsFirstThenFailedThenNeverNewestFirst() {
		List<HouseCollectUnit> units = new ArrayList<>(List.of(
			unit("11680", "202401", CollectUnitStatus.NEVER, 0, null),
			unit("11680", "202405", CollectUnitStatus.NEVER, 0, null),
			unit("11680", "202501", CollectUnitStatus.FAILED, 2, null),
			unit("11110", "202502", CollectUnitStatus.FAILED, 1, null),
			unit("11680", "202610", CollectUnitStatus.DONE, 0, TODAY.minusDays(1)),
			unit("11110", "202608", CollectUnitStatus.NEVER, 0, null),
			unit("11110", "202609", CollectUnitStatus.FAILED, 3, null),
			unit("11680", "202606", CollectUnitStatus.DONE, 0, TODAY.minusDays(1))));

		assertThat(labels(CollectUnitPriority.order(units, TODAY))).containsExactly(
			"11680/202610", "11110/202609", "11110/202608",
			"11110/202502", "11680/202501",
			"11680/202405", "11680/202401");
	}

	@Test
	void order_shouldExcludeDoneUnitsOutsideRecentWindowOnOrdinaryDays() {
		List<HouseCollectUnit> units = List.of(unit("11680", "202604", CollectUnitStatus.DONE, 0,
			TODAY.minusDays(30)));

		assertThat(CollectUnitPriority.order(units, TODAY)).isEmpty();
	}

	@Test
	void order_shouldAddRefreshOfThirdToTwelfthMonthsOnlyOnFirstOfMonth() {
		LocalDate first = LocalDate.of(2026, 10, 1);
		List<HouseCollectUnit> units = List.of(
			unit("11680", "202607", CollectUnitStatus.DONE, 0, first.minusDays(10)),
			unit("11680", "202510", CollectUnitStatus.DONE, 0, first.minusDays(10)),
			unit("11680", "202509", CollectUnitStatus.DONE, 0, first.minusDays(10)),
			unit("11680", "202608", CollectUnitStatus.DONE, 0, first.minusDays(10)),
			unit("11680", "202609", CollectUnitStatus.DONE, 0, first.minusDays(10)),
			unit("11680", "202606", CollectUnitStatus.DONE, 0, first));

		assertThat(labels(CollectUnitPriority.order(units, first))).containsExactly(
			"11680/202609", "11680/202608",
			"11680/202607", "11680/202510");
	}

	@Test
	void order_shouldIgnoreUnitsOutsideFortyMonthWindow() {
		List<HouseCollectUnit> units = List.of(unit("11680", "202306", CollectUnitStatus.NEVER, 0, null));

		assertThat(CollectUnitPriority.order(units, TODAY)).isEmpty();
	}
}
