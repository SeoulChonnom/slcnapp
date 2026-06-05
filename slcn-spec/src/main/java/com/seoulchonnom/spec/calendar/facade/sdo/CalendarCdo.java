package com.seoulchonnom.spec.calendar.facade.sdo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarCdo {
	@Schema(description = "캘린더 이름", example = "아영")
	private String name;
	@Schema(description = "배경 색상", example = "#FE9FC8")
	private String backgroundColor;
	@Schema(description = "테두리 색상", example = "#FE9FC8")
	private String borderColor;
	@Schema(description = "텍스트 색상", example = "#111111")
	private String textColor;
	@Schema(description = "편집 가능 여부", example = "true")
	private boolean editable;
	@Schema(description = "시작 시각 수정 가능 여부", example = "true")
	private boolean startEditable;
	@Schema(description = "기간 수정 가능 여부", example = "true")
	private boolean durationEditable;
	@Schema(description = "기본 선택 여부", example = "true")
	private boolean defaultSelected;
	@Schema(description = "정렬 순서", example = "1")
	private int sortOrder;
}
