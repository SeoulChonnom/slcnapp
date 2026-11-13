package com.seoulchonnom.spec.house.facade.sdo;

import java.math.BigDecimal;

import com.seoulchonnom.spec.house.entity.vo.SampleStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 과거 3개월 평균 -> 최근 3개월 평균 변동. sampleStatus가 INSUFFICIENT면 pct, changeManwon은 null이고
 * fromManwon/toManwon은 건수가 0인 쪽만 null이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceChangeRdo {
	private Long fromManwon;
	private Long toManwon;
	private BigDecimal pct;
	private Long changeManwon;
	private int sampleFrom;
	private int sampleTo;
	private SampleStatus sampleStatus;
}
