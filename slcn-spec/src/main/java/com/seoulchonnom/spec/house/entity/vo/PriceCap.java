package com.seoulchonnom.spec.house.entity.vo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 주택 가격 구간별 주담대 한도. 구간은 (이전 max, max]이고 마지막 항목만 maxPriceManwon이 null(상한 없음)이다.
 * JSON 컬럼 값이라 equals가 없으면 더티 체크가 매번 변경으로 판정한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PriceCap {
	private Long maxPriceManwon;
	private long capManwon;
}
