package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * keyword 검색이 매물(단지명/매물명)에 걸렸을 때만 채워지는 지역 목록 행의 필드.
 * ViewedPropertyBriefRdo와 형태가 같지만 화면이 그 매물로 바로 이동해야 해서 visitId가 더 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchedPropertyRdo {
	private String propertyId;
	private String visitId;
	private String complexName;
	private String name;
	private Integer interestLevel;
}
