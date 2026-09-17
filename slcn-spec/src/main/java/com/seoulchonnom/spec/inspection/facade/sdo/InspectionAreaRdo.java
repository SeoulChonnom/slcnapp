package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역 목록 한 행. 집계 필드는 전부 저장하지 않고 조회 시 계산한다 —
 * 임장 등록/삭제와 동기화가 어긋날 여지를 만들지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionAreaRdo {
	private String areaId;
	private String name;
	private String description;
	private int visitCount;
	private String firstVisitedAt;
	private String lastVisitedAt;
	private int totalPropertyCount;
	private InspectionVisitSummaryRdo latestVisit;
	/**
	 * 지역 전체에서 interestLevel이 가장 높은 매물. 임장 목록의 동명 필드는 회차 안에서만 고른다.
	 */
	private ViewedPropertyBriefRdo topProperty;
	private IncompleteSummaryRdo incompleteSummary;
	/**
	 * 대표 사진 최대 2건. 최신 회차의 COVER → 없으면 sortOrder 앞선 GALLERY 순.
	 */
	private List<FileBoxItemRdo> thumbnails = new ArrayList<>();
	private int totalImageCount;
}
