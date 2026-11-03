package com.seoulchonnom.spec.house.entity.vo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 중개보수 구간. 구간은 [이전 below, below)이고 마지막 항목만 belowManwon이 null이다.
 * capManwon이 null이면 한도가 없다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BrokerageTier {
	private Long belowManwon;
	private BigDecimal ratePct;
	private Long capManwon;
}
