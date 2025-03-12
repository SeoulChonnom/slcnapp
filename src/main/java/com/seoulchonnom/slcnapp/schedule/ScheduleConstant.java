package com.seoulchonnom.slcnapp.schedule;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleConstant {
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String RETRIEVE_SCHEDULE_COMPLETE_MESSAGE = "일정 조회에 성공하였습니다.";
    public static final String REGISTER_SCHEDULE_COMPLETE_MESSAGE = "일정 등록에 성공하였습니다.";

    public static final String INVALID_DATE_ERROR_MESSAGE = "올바르지 않은 날짜입니다.";
}
