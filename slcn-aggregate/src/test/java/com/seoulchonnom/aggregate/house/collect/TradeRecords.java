package com.seoulchonnom.aggregate.house.collect;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.spec.house.entity.vo.DealingType;

/**
 * 테스트용 TradeRecord 조립 도구.
 */
public final class TradeRecords {
	private TradeRecords() {
	}

	public static TradeRecord record(String aptSeq, LocalDate dealDate, long amount) {
		return new TradeRecord(aptSeq, "11680", "10300", "개포동", "0652", "0000", "개포우성3차", "개포로", "307", "0",
			new BigDecimal("104.43"), amount, dealDate, 7, null, 1984, false, null, DealingType.BROKER);
	}

	public static TradeRecord cancelled(TradeRecord record) {
		return new TradeRecord(record.aptSeq(), record.lawdCd(), record.umdCd(), record.umdName(), record.bonbun(),
			record.bubun(), record.aptName(), record.roadName(), record.roadBonbun(), record.roadBubun(),
			record.exclusiveArea(), record.amountManwon(), record.dealDate(), record.floor(), record.aptDong(),
			record.buildYear(), true, LocalDate.of(2026, 9, 30), record.dealingType());
	}
}
