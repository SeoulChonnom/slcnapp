package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 매물 상세. 문답을 포함하므로 임장 상세의 properties[]도 이 타입을 쓴다.
 *
 * areaId/areaName/visitedAt은 breadcrumb(임장 › 성수동 › 2026.09.17 임장 › 트리마제 101동 1203호)을
 * 그리기 위한 문맥이고, prevProperty/nextProperty는 같은 임장 안에서 매물 정렬 순서 기준
 * 이전/다음 1건이다(없으면 null). visitId 없이 매물을 직행 조회하는 화면에서 필요해졌다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ViewedPropertyDetailRdo {
	private String propertyId;
	private String inspectionVisitId;
	private String areaId;
	private String areaName;
	private String visitedAt;
	private String complexName;
	private String name;
	private String memo;
	private String oneLineReview;
	private String pros;
	private String cons;
	private Integer interestLevel;
	private InspectionStatus status;
	private int sortOrder;
	private List<String> tags = new ArrayList<>();
	private FileBoxItemRdo cover;
	private List<FileBoxItemRdo> photos = new ArrayList<>();
	private List<PropertyAnswerRdo> answers = new ArrayList<>();
	private IncompleteSummaryRdo incompleteSummary;
	private ViewedPropertyBriefRdo prevProperty;
	private ViewedPropertyBriefRdo nextProperty;
}
