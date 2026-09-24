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
	private List<String> tags;
	private List<FileBoxItemUdo> files;
}
