package com.seoulchonnom.spec.schedule.constant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleConstant {
	public static final ZoneId SCHEDULE_ZONE_ID = ZoneId.of("Asia/Seoul");
	public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
	public static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

	public static final String RETRIEVE_SCHEDULE_COMPLETE_MESSAGE = "일정 조회에 성공하였습니다.";
	public static final String REGISTER_SCHEDULE_COMPLETE_MESSAGE = "일정 등록에 성공하였습니다.";
	public static final String MODIFY_SCHEDULE_COMPLETE_MESSAGE = "일정 수정에 성공하였습니다.";
	public static final String DELETE_SCHEDULE_COMPLETE_MESSAGE = "일정 삭제에 성공하였습니다.";
	public static final String HARD_DELETE_SCHEDULE_COMPLETE_MESSAGE = "일정 데이터 삭제에 성공하였습니다.";

	public static final String INVALID_DATE_ERROR_MESSAGE = "올바르지 않은 날짜입니다.";
	public static final String SCHEDULE_NOT_FOND_ERROR_MESSAGE = "올바르지 않은 스케쥴 정보입니다.";

	public static LocalDateTime parseScheduleDateTime(String dateTime) {
		return OffsetDateTime.parse(dateTime, ISO_DATE_TIME_FORMATTER)
			.atZoneSameInstant(SCHEDULE_ZONE_ID)
			.toLocalDateTime();
	}

	public static LocalDate parseScheduleDate(String date) {
		return LocalDate.parse(date, ISO_DATE_FORMATTER);
	}

	public static LocalDateTime parseMutationDateTime(String value, boolean isAllDay) {
		if (isAllDay) {
			return parseScheduleDate(value).atStartOfDay();
		}

		return parseScheduleDateTime(value);
	}

	public static String formatScheduleDateTime(LocalDateTime dateTime, boolean isAllDay) {
		if (isAllDay) {
			return dateTime.toLocalDate().format(ISO_DATE_FORMATTER);
		}

		return dateTime.atZone(SCHEDULE_ZONE_ID).format(ISO_DATE_TIME_FORMATTER);
	}
}
