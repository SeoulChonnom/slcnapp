package com.seoulchonnom.spec.inspection.facade.sdo;

import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역 전체 매물 경량 목록 한 행.
 *
 * FE가 같은 지역 안에서 complexName+name이 일치하는 매물을 회차 간으로 묶어
 * "2026.06.02 ★★★☆☆ 3 → 2026.09.17 ★★★★★ 5" 같은 변화를 보여주는 데 쓴다.
 * 그룹핑은 FE가 하므로 이 타입은 정렬된 평면 목록이고, answers는 담지 않는다 —
 * ViewedPropertySummaryPdo(TEXT 컬럼을 읽지 않는 projection)로만 채운다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AreaViewedPropertyRdo {
	private String propertyId;
	private String visitId;
	private String visitedAt;
	private String complexName;
	private String name;
	private Integer interestLevel;
	private InspectionStatus status;
}
