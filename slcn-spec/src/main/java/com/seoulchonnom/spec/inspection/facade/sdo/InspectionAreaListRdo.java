package com.seoulchonnom.spec.inspection.facade.sdo;

import com.seoulchonnom.spec.common.response.PageRdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GET /inspection-areas 응답. 공통 페이지 계약(items/totalCount/hasNext)에
 * 지역 목록 전용 전역 집계 두 개를 얹는다.
 *
 * revisitIntentCounts/totals는 페이지 계약과 성격이 다르다 — 페이지(items/totalCount/hasNext)는
 * keyword/revisitIntent/page에 따라 바뀌지만, 이 둘은 필터 칩과 헤더 문구를 위해 필터를
 * 걸기 전 전체 값을 항상 보여줘야 해서 어떤 필터에도 영향받지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionAreaListRdo extends PageRdo<InspectionAreaRdo> {
	private RevisitIntentCountsRdo revisitIntentCounts;
	private InspectionAreaTotalsRdo totals;
}
