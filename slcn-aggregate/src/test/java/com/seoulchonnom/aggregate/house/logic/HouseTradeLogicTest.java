package com.seoulchonnom.aggregate.house.logic;

import static com.seoulchonnom.aggregate.house.collect.TradeRecords.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.house.collect.ComplexNameNormalizer;
import com.seoulchonnom.aggregate.house.collect.DedupKeyFactory;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult;
import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.aggregate.house.store.HouseTradeStore;
import com.seoulchonnom.aggregate.house.store.HouseTradeStore.UpsertResult;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;
import com.seoulchonnom.spec.house.entity.HouseTrade;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.CollectUnitStatus;

class HouseTradeLogicTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

	private final HouseComplexLogic complexLogic = mock(HouseComplexLogic.class);
	private final HouseCollectUnitLogic unitLogic = mock(HouseCollectUnitLogic.class);
	private final HouseTradeStore tradeStore = mock(HouseTradeStore.class);
	private final HouseTradeLogic logic = new HouseTradeLogic(complexLogic, unitLogic, tradeStore);
	private final HouseCollectUnit unit = HouseCollectUnit.builder().lawdCd("11680").dealYm("202609")
		.status(CollectUnitStatus.NEVER).build();

	private void resolveAs(String... aptSeqs) {
		when(complexLogic.resolve(eq("11680"), anyMap())).thenAnswer(invocation -> {
			Map<String, TradeRecord> latest = invocation.getArgument(1);
			Map<String, String> ids = new java.util.HashMap<>();
			latest.keySet().forEach(key -> ids.put(key, "C-" + key));
			return new ComplexResolution(ids, latest.size(), 0);
		});
	}

	@Test
	void applyUnit_shouldUpsertEveryRowAndCountResults() {
		resolveAs();
		TradeRecord a = record("11680-283", LocalDate.of(2026, 9, 22), 295000);
		TradeRecord b = record("11680-283", LocalDate.of(2026, 9, 22), 295000);
		TradeRecord c = cancelled(record("11680-3741", LocalDate.of(2026, 9, 14), 310000));
		when(tradeStore.upsert(any())).thenReturn(UpsertResult.INSERTED, UpsertResult.UNCHANGED,
			UpsertResult.UPDATED);
		when(tradeStore.markRemovedExcept(any(), any(), any())).thenReturn(4);

		TradeApplyResult result = logic.applyUnit(unit, TradeFetchResult.ok(List.of(a, b, c), 2), TODAY);

		assertThat(result.fetched()).isEqualTo(3);
		assertThat(result.inserted()).isEqualTo(1);
		assertThat(result.unchanged()).isEqualTo(1);
		assertThat(result.updated()).isEqualTo(1);
		assertThat(result.cancelled()).isEqualTo(1);
		assertThat(result.removed()).isEqualTo(4);
		assertThat(result.skippedRows()).isEqualTo(2);
		assertThat(result.complexCreated()).isEqualTo(2);
		assertThat(result.emptyResponse()).isFalse();
	}

	@Test
	void applyUnit_shouldSendDedupKeysWithOccurrenceAndUnitScopeToStore() {
		resolveAs();
		TradeRecord a = record("11680-283", LocalDate.of(2026, 9, 22), 295000);
		List<String> expectedKeys = DedupKeyFactory.create(List.of(a, a));
		when(tradeStore.upsert(any())).thenReturn(UpsertResult.INSERTED);

		logic.applyUnit(unit, TradeFetchResult.ok(List.of(a, a), 0), TODAY);

		ArgumentCaptor<HouseTrade> trades = ArgumentCaptor.forClass(HouseTrade.class);
		verify(tradeStore, times(2)).upsert(trades.capture());
		assertThat(trades.getAllValues()).extracting(HouseTrade::getDedupKey).containsExactlyElementsOf(expectedKeys);
		assertThat(trades.getAllValues()).allSatisfy(trade -> {
			assertThat(trade.getLawdCd()).isEqualTo("11680");
			assertThat(trade.getDealYm()).isEqualTo("202609");
			assertThat(trade.getComplexId()).isEqualTo("C-11680-283");
			assertThat(trade.isRemoved()).isFalse();
			assertThat(trade.isOutlier()).isFalse();
		});
		verify(tradeStore).markRemovedExcept("11680", "202609", expectedKeys);
	}

	@Test
	void applyUnit_shouldMarkUnitSucceededOnlyWhenComplete() {
		resolveAs();
		when(tradeStore.upsert(any())).thenReturn(UpsertResult.INSERTED);
		TradeRecord a = record("11680-283", LocalDate.of(2026, 9, 22), 295000);

		logic.applyUnit(unit, TradeFetchResult.ok(List.of(a), 0), TODAY);

		verify(unitLogic).markSucceeded(unit.getId(), TODAY);
	}

	@Test
	void applyUnit_shouldNotJudgeRemovedNorFinishUnitWhenIncomplete() {
		resolveAs();
		when(tradeStore.upsert(any())).thenReturn(UpsertResult.INSERTED);
		TradeRecord a = record("11680-283", LocalDate.of(2026, 9, 22), 295000);

		logic.applyUnit(unit, TradeFetchResult.stopped(TradeFetchResult.Outcome.FAILED, List.of(a), 0), TODAY);

		verify(tradeStore).upsert(any());
		verify(tradeStore, never()).markRemovedExcept(any(), any(), any());
		verify(unitLogic, never()).markSucceeded(any(), any());
	}

	@Test
	void applyUnit_shouldFinishUnitWithoutRemovingAnythingWhenResponseIsEmpty() {
		TradeApplyResult result = logic.applyUnit(unit, TradeFetchResult.ok(List.of(), 0), TODAY);

		assertThat(result.emptyResponse()).isTrue();
		assertThat(result.removed()).isZero();
		verify(complexLogic, never()).resolve(any(), any());
		verify(tradeStore, never()).upsert(any());
		verify(tradeStore).markRemovedExcept("11680", "202609", List.of());
		verify(unitLogic).markSucceeded(unit.getId(), TODAY);
	}

	@Test
	void applyUnit_shouldPickLatestDealRecordPerComplexForDisplayInfo() {
		resolveAs();
		when(tradeStore.upsert(any())).thenReturn(UpsertResult.INSERTED);
		TradeRecord older = record("11680-283", LocalDate.of(2026, 9, 1), 100);
		TradeRecord newer = record("11680-283", LocalDate.of(2026, 9, 28), 200);
		TradeRecord sameDayLater = record("11680-283", LocalDate.of(2026, 9, 28), 300);

		logic.applyUnit(unit, TradeFetchResult.ok(List.of(older, newer, sameDayLater, older), 0), TODAY);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, TradeRecord>> latest = ArgumentCaptor.forClass(Map.class);
		verify(complexLogic).resolve(eq("11680"), latest.capture());
		assertThat(latest.getValue()).containsOnlyKeys("11680-283");
		assertThat(latest.getValue().get("11680-283").amountManwon()).isEqualTo(300);
	}

	@Test
	void applyUnit_shouldUseAlternativeKeyWhenAptSeqIsBlank() {
		resolveAs();
		when(tradeStore.upsert(any())).thenReturn(UpsertResult.INSERTED);
		TradeRecord noSeq = record(null, LocalDate.of(2026, 9, 22), 295000);
		String expected = ComplexNameNormalizer.alternativeAptSeq("11680", "10300", "0652", "0000", "개포우성3차");

		logic.applyUnit(unit, TradeFetchResult.ok(List.of(noSeq), 0), TODAY);

		ArgumentCaptor<HouseTrade> trade = ArgumentCaptor.forClass(HouseTrade.class);
		verify(tradeStore).upsert(trade.capture());
		assertThat(trade.getValue().getComplexId()).isEqualTo("C-" + expected);
	}

	@Test
	void applyUnit_shouldSkipRowsThatCannotBeIdentified() {
		resolveAs();
		TradeRecord base = record(null, LocalDate.of(2026, 9, 22), 295000);
		TradeRecord noName = new TradeRecord(null, base.lawdCd(), base.umdCd(), base.umdName(), base.bonbun(),
			base.bubun(), null, base.roadName(), base.roadBonbun(), base.roadBubun(), base.exclusiveArea(), 1,
			base.dealDate(), 1, null, null, false, null, base.dealingType());

		TradeApplyResult result = logic.applyUnit(unit, TradeFetchResult.ok(List.of(noName), 0), TODAY);

		assertThat(result.skippedRows()).isEqualTo(1);
		assertThat(result.fetched()).isZero();
		verify(tradeStore, never()).upsert(any());
	}

	@Test
	void applyUnit_shouldAssignAreaBucketFromExclusiveArea() {
		resolveAs();
		when(tradeStore.upsert(any())).thenReturn(UpsertResult.INSERTED);
		TradeRecord base = record("11680-283", LocalDate.of(2026, 9, 22), 295000);
		List<TradeRecord> rows = List.of(withArea(base, "59.97"), withArea(base, "84.99"), withArea(base, "104.43"));

		logic.applyUnit(unit, TradeFetchResult.ok(rows, 0), TODAY);

		ArgumentCaptor<HouseTrade> trades = ArgumentCaptor.forClass(HouseTrade.class);
		verify(tradeStore, times(3)).upsert(trades.capture());
		assertThat(trades.getAllValues()).extracting(HouseTrade::getAreaBucket)
			.containsExactly(AreaBucket.B59, AreaBucket.B84, AreaBucket.OTHER);
	}

	private TradeRecord withArea(TradeRecord r, String area) {
		return new TradeRecord(r.aptSeq(), r.lawdCd(), r.umdCd(), r.umdName(), r.bonbun(), r.bubun(), r.aptName(),
			r.roadName(), r.roadBonbun(), r.roadBubun(), new BigDecimal(area), r.amountManwon(), r.dealDate(),
			r.floor(), r.aptDong(), r.buildYear(), r.cancelled(), r.cancelledDate(), r.dealingType());
	}
}
