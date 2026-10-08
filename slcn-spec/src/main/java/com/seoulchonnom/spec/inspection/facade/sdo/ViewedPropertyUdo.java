package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 부분 수정 의미론은 InspectionVisitUdo와 같다 — 스칼라는 덮어쓰고 컬렉션은 유지한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ViewedPropertyUdo {
	private String complexName;
	private String name;
	private String memo;
	private String oneLineReview;
	private String pros;
	private String cons;
	private Integer interestLevel;
	/** 생략하거나 null이면 위치 없음(수정 시에는 저장된 위치를 지운다). */
	private PropertyLocationInputSdo location;
	private List<String> tags;
	private List<FileBoxItemUdo> files;
}
