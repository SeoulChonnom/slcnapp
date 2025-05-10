package com.seoulchonnom.slcnapp.schedule.dto;

import com.seoulchonnom.slcnapp.schedule.domain.ScheduleCategory;
import com.seoulchonnom.slcnapp.schedule.domain.ScheduleState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleModifyRequest {
    @Schema(description = "스케쥴 ID")
    private String id;
    @Schema(description = "캘린더 ID", example = "cal1")
    private String calendarId;
    @Schema(description = "제목", example = "title")
    private String title;
    @Schema(description = "설명", example = "body")
    private String body;

    @Schema(description = "종일 일정 여부", example = "false")
    private boolean isAllday;

    @Schema(description = "시작 일시", example = "2025-03-01 00:00:00")
    private String start;
    @Schema(description = "종료 일시", example = "2025-03-01 01:00:00")
    private String end;

    @Schema(description = "일정 장소 이동시간", example = "1000")
    private long goingDuration;
    @Schema(description = "다음 일정 장소 이동시간", example = "1000")
    private long comingDuration;

    @Schema(description = "장소", example = "location")
    private String location;
    @Schema(description = "종류(milestone, task, allday, time)", example = "time")
    private ScheduleCategory category;
    @Schema(description = "task 일정 카테고리", example = "dueDateClass")
    private String dueDateClass;
    @Schema(description = "task 일정 카테고리", example = "일정 반복 규칙")
    private String recurrenceRule;
    @Schema(description = "일정 상태(Busy, Free)", example = "Busy")
    private ScheduleState state;

    @Schema(description = "노출 여부", example = "false")
    private boolean isVisible;
    @Schema(description = "미정 여부", example = "false")
    private boolean isPending;
    @Schema(description = "일정 강조 여부", example = "false")
    private boolean isFocused;
    @Schema(description = "읽기 전용 여부", example = "false")
    private boolean isReadOnly;
    @Schema(description = "개인 전용 여부", example = "false")
    private boolean isPrivate;

    @Schema(description = "일정 색 설정", example = "#03bd9e")
    private String color;
    @Schema(description = "일정 배경 색 설정", example = "#03bd9e")
    private String backgroundColor;
    @Schema(description = "드래그 시 배경 색 설정", example = "#03bd9e")
    private String dragBackgroundColor;
    @Schema(description = "테두리 색 설정", example = "#03bd9e")
    private String borderColor;
    @Schema(description = "커스텀 색 설정(CSS)", example = "{}")
    private String customStyle;


    public void setIsAllday(boolean isAllday) {
        this.isAllday = isAllday;
    }

    public void setIsVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public void setIsPending(boolean isPending) {
        this.isPending = isPending;
    }

    public void setIsFocused(boolean isFocused) {
        this.isFocused = isFocused;
    }

    public void setIsReadOnly(boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public void setIsPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}
