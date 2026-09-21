package com.seoulchonnom.aggregate.inspection.store.projection;

import java.time.LocalDateTime;

/**
 * 지역 목록 검색(A-③)에서 keyword가 매물(단지명/매물명)에 걸렸을 때, 그 지역의
 * matchedProperty를 고르기 위한 후보 하나. ViewedPropertyRepository의 네이티브 쿼리가
 * viewed_property와 inspection_visit을 조인해 채운다.
 */
public interface MatchedPropertyPdo {
	String getId();

	String getComplexName();

	String getName();

	Integer getInterestLevel();

	int getSortOrder();

	String getVisitId();

	LocalDateTime getVisitedAt();

	String getAreaId();
}
