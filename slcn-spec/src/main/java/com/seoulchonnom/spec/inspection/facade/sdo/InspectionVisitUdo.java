package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 부분 수정 의미론(도메인 공통): 스칼라는 생략하면 null로 덮어쓰고,
 * 컬렉션(tags, files)은 생략하면 기존 값을 유지한다.
 * travel의 tags는 반대로 동작하니 그 코드를 그대로 옮겨오지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionVisitUdo {
	private String visitedAt;
	private String memo;
	private RevisitIntent revisitIntent;
	private String oneLineReview;
	private String pros;
	private String cons;
	private List<String> tags;
	private List<FileBoxItemUdo> files;
}
