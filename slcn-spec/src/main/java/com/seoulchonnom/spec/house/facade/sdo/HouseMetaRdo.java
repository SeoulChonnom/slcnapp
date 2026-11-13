package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseMetaRdo {
	/** yyyy-MM-dd. 날짜는 문자열로 내려 직렬화 설정에 기대지 않는다(다른 Rdo와 같다). 최근 3개월 수집 단위 75개 중 가장 이른 마지막 성공일. 하나라도 없으면 null */
	private String dataAsOf;
	/** STATS_BUILD의 마지막 SUCCESS 실행일. 없으면 null */
	private String statsComputedDate;
}
