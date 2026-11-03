package com.seoulchonnom.spec.house.entity.vo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 생애최초 취득세 감면. 감면 한도와 감면이 적용되는 최대 주택 가격.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FirstTimeRelief {
	private long capManwon;
	private long maxPriceManwon;
}
