package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.collect.ComplexNameNormalizer;
import com.seoulchonnom.aggregate.house.collect.DedupKeyFactory;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult;
import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.aggregate.house.store.HouseTradeStore;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;
import com.seoulchonnom.spec.house.entity.HouseTrade;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseTradeLogic {
	private final HouseComplexLogic houseComplexLogic;
	private final HouseCollectUnitLogic houseCollectUnitLogic;
	private final HouseTradeStore houseTradeStore;

	/**
	 * 수집 단위 하나를 반영한다. 단위 1개 = 트랜잭션 1개라서 실패하면 그 단위만 롤백된다.
	 *
	 * 모든 페이지를 받은 결과(complete)일 때만 원천 삭제 판정과 단위 DONE 처리를 한다.
	 * 일부만 받은 결과는 거래 upsert까지만 하고 단위 상태는 그대로 둔다.
	 */
	@Transactional
	public TradeApplyResult applyUnit(HouseCollectUnit unit, TradeFetchResult result, LocalDate today) {
		int skipped = result.skippedRows();
		List<TradeRecord> keyed = new ArrayList<>();
		for (TradeRecord record : result.records()) {
			String aptSeq = resolveAptSeq(unit, record);
			if (aptSeq == null) {
				skipped++;
			} else {
				keyed.add(record.withAptSeq(aptSeq));
			}
		}

		// 가장 최근 계약일 레코드가 단지 표시 정보의 기준이다. 같은 날이면 응답에서 뒤에 나온 것이 이긴다.
		Map<String, TradeRecord> latestByAptSeq = new HashMap<>();
		for (TradeRecord record : keyed) {
			latestByAptSeq.merge(record.aptSeq(), record,
				(old, incoming) -> incoming.dealDate().isBefore(old.dealDate()) ? old : incoming);
		}
		ComplexResolution complexes = latestByAptSeq.isEmpty()
			? new ComplexResolution(Map.of(), 0, 0)
			: houseComplexLogic.resolve(unit.getLawdCd(), latestByAptSeq);

		List<String> dedupKeys = DedupKeyFactory.create(keyed);
		int inserted = 0;
		int updated = 0;
		int unchanged = 0;
		int cancelled = 0;
		for (int i = 0; i < keyed.size(); i++) {
			TradeRecord record = keyed.get(i);
			if (record.cancelled()) {
				cancelled++;
			}
			HouseTradeStore.UpsertResult upsert = houseTradeStore
				.upsert(toTrade(unit, record, dedupKeys.get(i), complexes.complexIdByAptSeq().get(record.aptSeq())));
			switch (upsert) {
				case INSERTED -> inserted++;
				case UPDATED -> updated++;
				case UNCHANGED -> unchanged++;
			}
		}

		int removed = 0;
		boolean emptyResponse = false;
		if (result.complete()) {
			// 응답이 0건이면 키 목록이 비어 removed 쿼리를 실행하지 않는다. 일시적인 빈 응답으로
			// 한 달치 거래가 통계에서 사라지는 쪽이 오래된 몇 건이 남는 쪽보다 위험하다고 보고 단위 전체 삭제로 보지 않는다.
			emptyResponse = dedupKeys.isEmpty();
			removed = houseTradeStore.markRemovedExcept(unit.getLawdCd(), unit.getDealYm(), dedupKeys);
			houseCollectUnitLogic.markSucceeded(unit.getId(), today);
		}
		return new TradeApplyResult(keyed.size(), inserted, updated, unchanged, cancelled, removed, skipped,
			complexes.created(), complexes.refreshed(), emptyResponse);
	}

	/**
	 * aptSeq가 있으면 그대로, 비어 있으면 대체 키. 대체 키를 만들 단서(단지명, 법정동)도 없으면 null이라 그 행은 버린다.
	 */
	private String resolveAptSeq(HouseCollectUnit unit, TradeRecord record) {
		if (record.aptSeq() != null) {
			return record.aptSeq();
		}
		if (record.aptName() == null || record.umdCd() == null) {
			return null;
		}
		return ComplexNameNormalizer.alternativeAptSeq(unit.getLawdCd(), record.umdCd(), record.bonbun(),
			record.bubun(), record.aptName());
	}

	private HouseTrade toTrade(HouseCollectUnit unit, TradeRecord record, String dedupKey, String complexId) {
		return HouseTrade.builder()
			.dedupKey(dedupKey)
			.complexId(complexId)
			.lawdCd(unit.getLawdCd())
			.dealYm(unit.getDealYm())
			.dealDate(record.dealDate())
			.exclusiveArea(record.exclusiveArea())
			.areaBucket(AreaBucket.of(record.exclusiveArea()))
			.amountManwon(record.amountManwon())
			.floor(record.floor())
			.aptDong(record.aptDong())
			.buildYear(record.buildYear())
			.dealingType(record.dealingType())
			.cancelled(record.cancelled())
			.cancelledDate(record.cancelledDate())
			.build();
	}
}
