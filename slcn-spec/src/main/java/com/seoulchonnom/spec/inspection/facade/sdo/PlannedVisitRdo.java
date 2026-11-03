package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역 행에 붙는 계획(미완료) 임장 1건. "예정"인지 "미완료"인지는 FE가 visitedAt을 오늘과 비교해 정한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlannedVisitRdo {
	private String inspectionVisitId;
	private String visitedAt;
}
