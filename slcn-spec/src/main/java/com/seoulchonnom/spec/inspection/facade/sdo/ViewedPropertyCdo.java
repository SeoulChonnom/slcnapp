package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 등록 시 DRAFT로 만들고 활성 질문의 답변 행을 함께 생성한다.
 * complexName은 등록 시 필수다. name(동·호수)은 계획 단계에서 비워 둘 수 있지만 완료 조건이라 비어 있으면 완료할 수 없다.
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
