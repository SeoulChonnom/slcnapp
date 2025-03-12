package com.seoulchonnom.slcnapp.schedule.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "알림 송신 DTO")
public class ScheduleSearchRequest {
    @Schema(description = "검색 년도", example = "2025")
    private int year;
    @Schema(description = "검색 달", example = "3")
    private int month;
}
