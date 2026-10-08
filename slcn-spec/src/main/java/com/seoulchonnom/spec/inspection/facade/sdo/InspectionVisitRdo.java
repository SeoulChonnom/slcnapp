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
 * 임장 목록 한 행. topInterestProperty는 이 회차 안에서만 고른다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionVisitRdo {
	private String inspectionVisitId;
	private InspectionAreaBriefRdo area;
	private String visitedAt;
	/** 최초 완료 시각. 한 번도 완료되지 않았으면 null. visitedAt과 같은 형식. */
	private String completedAt;
	private String oneLineReview;
	private RevisitIntent revisitIntent;
	private InspectionStatus status;
	private int propertyCount;
	private ViewedPropertyBriefRdo topInterestProperty;
	private List<String> tags = new ArrayList<>();
	private FileBoxItemRdo cover;
	private IncompleteSummaryRdo incompleteSummary;
}
