package com.seoulchonnom.aggregate.inspection.store.projection;

import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;

/**
 * 목록·집계 전용. answers TEXT 컬럼을 읽지 않는다는 것이 이 타입의 존재 이유다.
 * 매물 한 건당 수 KB인 문답 스냅샷을 목록 N행마다 끌어오면 목록이 답변 본문 조회가 된다.
 */
public interface ViewedPropertySummaryPdo {
	String getId();

	String getInspectionVisitId();

	String getComplexName();

	String getName();

	Integer getInterestLevel();

	InspectionStatus getStatus();

	int getSortOrder();

	int getRequiredAnswerCount();

	int getUnansweredRequiredCount();

	Long getRegisteredTime();
}
