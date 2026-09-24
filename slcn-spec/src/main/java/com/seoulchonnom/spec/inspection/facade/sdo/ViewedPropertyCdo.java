package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 등록 시 DRAFT로 만들고 활성 질문의 답변 행을 함께 생성한다.
 * complexName과 name은 완료 조건이며 DRAFT 등록 시에도 매물 식별에 필요하다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ViewedPropertyCdo {
	private String complexName;
	private String name;
	private String memo;
	private String oneLineReview;
	private String pros;
	private String cons;
	private Integer interestLevel;
	private List<String> tags;
	private List<FileBoxItemCdo> files;
}
