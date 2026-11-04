package com.seoulchonnom.aggregate.house.collect;

import static com.seoulchonnom.aggregate.house.collect.TradeRecords.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class DedupKeyFactoryTest {
	private static final LocalDate DAY = LocalDate.of(2026, 9, 22);

	@Test
	void create_shouldGiveDifferentKeysToIdenticalRowsByOccurrence() {
		TradeRecord row = record("11680-283", DAY, 295000);

		List<String> keys = DedupKeyFactory.create(List.of(row, row));

		assertThat(keys).hasSize(2).doesNotHaveDuplicates();
		assertThat(keys.get(0)).hasSize(64).matches("[0-9a-f]{64}");
	}

	@Test
	void create_shouldBeStableAcrossCalls() {
		List<TradeRecord> rows = List.of(record("11680-283", DAY, 295000), record("11680-283", DAY, 295000));

		assertThat(DedupKeyFactory.create(rows)).isEqualTo(DedupKeyFactory.create(rows));
	}

	@Test
	void create_shouldKeepSameKeyWhenCancellationChanges() {
		TradeRecord row = record("11680-283", DAY, 295000);

		assertThat(DedupKeyFactory.create(List.of(cancelled(row)))).isEqualTo(DedupKeyFactory.create(List.of(row)));
	}

	@Test
	void create_shouldDifferWhenAmountOrDateDiffers() {
		String base = DedupKeyFactory.create(List.of(record("11680-283", DAY, 295000))).get(0);

		assertThat(DedupKeyFactory.create(List.of(record("11680-283", DAY, 295001))).get(0)).isNotEqualTo(base);
		assertThat(DedupKeyFactory.create(List.of(record("11680-283", DAY.plusDays(1), 295000))).get(0))
			.isNotEqualTo(base);
		assertThat(DedupKeyFactory.create(List.of(record("11680-284", DAY, 295000))).get(0)).isNotEqualTo(base);
	}

	@Test
	void create_shouldHandleNullFieldsAsEmpty() {
		TradeRecord base = record("11680-283", DAY, 295000);
		TradeRecord nulls = new TradeRecord("11680-283", base.lawdCd(), base.umdCd(), base.umdName(), base.bonbun(),
			base.bubun(), base.aptName(), base.roadName(), base.roadBonbun(), base.roadBubun(), base.exclusiveArea(),
			295000, DAY, null, null, null, false, null, base.dealingType());
		TradeRecord withFloor = new TradeRecord("11680-283", base.lawdCd(), base.umdCd(), base.umdName(),
			base.bonbun(), base.bubun(), base.aptName(), base.roadName(), base.roadBonbun(), base.roadBubun(),
			base.exclusiveArea(), 295000, DAY, 3, null, null, false, null, base.dealingType());

		assertThat(DedupKeyFactory.create(List.of(nulls))).hasSize(1);
		assertThat(DedupKeyFactory.create(List.of(nulls))).isNotEqualTo(DedupKeyFactory.create(List.of(withFloor)));
	}

	@Test
	void create_shouldTreatAreaScaleDifferencesAsSame() {
		TradeRecord a = record("11680-283", DAY, 295000);
		TradeRecord b = new TradeRecord(a.aptSeq(), a.lawdCd(), a.umdCd(), a.umdName(), a.bonbun(), a.bubun(),
			a.aptName(), a.roadName(), a.roadBonbun(), a.roadBubun(), new BigDecimal("104.4300"), a.amountManwon(),
			a.dealDate(), a.floor(), a.aptDong(), a.buildYear(), false, null, a.dealingType());

		assertThat(DedupKeyFactory.create(List.of(b))).isEqualTo(DedupKeyFactory.create(List.of(a)));
	}
}
