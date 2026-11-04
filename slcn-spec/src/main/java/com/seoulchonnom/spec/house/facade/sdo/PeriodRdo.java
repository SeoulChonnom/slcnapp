package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PeriodRdo {
	/** yyyy-MM-dd. 집계 창의 첫날 */
	private String from;
	/** yyyy-MM-dd. 집계 기준일(통계 계산일) */
	private String to;
}
