package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역 목록의 latestVisit과 지역 상세의 visits[]가 공유하는 회차 요약.
 * latestVisit에서는 propertyCount/incompleteSummary/cover가 비어 있을 수 있다.
 * 형태를 갈라 두면 매퍼가 둘로 늘고 FE가 두 모양을 다뤄야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionVisitSummaryRdo {
	private String visitId;
	private String visitedAt;
	private String oneLineReview;
	private RevisitIntent revisitIntent;
	private InspectionStatus status;
	private List<String> tags = new ArrayList<>();
	private Integer propertyCount;
	private IncompleteSummaryRdo incompleteSummary;
	private FileBoxItemRdo cover;
}
