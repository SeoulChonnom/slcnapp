package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * areaId 또는 인라인 area 중 하나가 필수다. 임장은 항상 DRAFT로 생성된다.
 * files의 targetType/targetId는 서버가 INSPECTION_VISIT/null로 확정한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionVisitCdo {
	private String areaId;
	private InspectionAreaCdo area;
	private String visitedAt;
	private String memo;
	private RevisitIntent revisitIntent;
	private String oneLineReview;
	private String pros;
	private String cons;
	private List<String> tags;
	private List<FileBoxItemCdo> files;
}
