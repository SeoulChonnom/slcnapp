package com.seoulchonnom.aggregate.house.collect;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.spec.house.entity.vo.FarSource;

/**
 * 건축물대장에서 읽은 단지 정보. 값이 끝내 없는 필드는 null이다(0도 빈 값으로 본다).
 *
 * @param farSource 용적률을 실제로 가져온 곳. 용적률이 없으면 null
 */
public record RegisterSummary(Integer households, BigDecimal farPct, BigDecimal bcrPct, LocalDate approvalDate,
	FarSource farSource) {
	public boolean isEmpty() {
		return households == null && farPct == null && bcrPct == null && approvalDate == null;
	}
}
