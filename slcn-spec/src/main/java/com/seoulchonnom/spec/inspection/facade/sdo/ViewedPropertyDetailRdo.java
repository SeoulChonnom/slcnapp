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
 */
@Getter
@Setter
@NoArgsConstructor
public class ViewedPropertyDetailRdo {
	private String propertyId;
	private String inspectionVisitId;
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
}
