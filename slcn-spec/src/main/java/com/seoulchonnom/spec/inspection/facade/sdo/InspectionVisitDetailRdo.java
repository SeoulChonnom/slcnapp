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
 * 임장 상세. properties는 sortOrder 오름차순 평면 배열이다 —
 * 단지별 그룹핑은 서버가 하지 않는다. 사용자 정렬과 그룹 순서가 충돌하고,
 * VisitedComplex 도입 시 응답 형태가 두 번 바뀐다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionVisitDetailRdo {
	private String inspectionVisitId;
	private InspectionAreaBriefRdo area;
	private String visitedAt;
	private String memo;
	private RevisitIntent revisitIntent;
	private String oneLineReview;
	private String pros;
	private String cons;
	private InspectionStatus status;
	private List<String> tags = new ArrayList<>();
	private List<ViewedPropertyDetailRdo> properties = new ArrayList<>();
	private FileBoxItemRdo cover;
	private List<FileBoxItemRdo> photos = new ArrayList<>();
	private IncompleteSummaryRdo incompleteSummary;
}
