package com.seoulchonnom.aggregate.house.collect;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.spec.house.entity.vo.DealingType;

/**
 * 실거래 응답 한 행을 우리 형식으로 옮긴 것. 게이트웨이 밖으로는 이것만 나간다.
 * 숫자로 오는 코드는 자릿수를 맞춘 문자열이다(lawdCd·umdCd 5자리, bonbun·bubun 4자리).
 */
public record TradeRecord(
	String aptSeq,
	String lawdCd,
	String umdCd,
	String umdName,
	String bonbun,
	String bubun,
	String aptName,
	String roadName,
	String roadBonbun,
	String roadBubun,
	BigDecimal exclusiveArea,
	long amountManwon,
	LocalDate dealDate,
	Integer floor,
	String aptDong,
	Integer buildYear,
	boolean cancelled,
	LocalDate cancelledDate,
	DealingType dealingType) {

	public TradeRecord withAptSeq(String aptSeq) {
		return new TradeRecord(aptSeq, lawdCd, umdCd, umdName, bonbun, bubun, aptName, roadName, roadBonbun,
			roadBubun, exclusiveArea, amountManwon, dealDate, floor, aptDong, buildYear, cancelled, cancelledDate,
			dealingType);
	}
}
