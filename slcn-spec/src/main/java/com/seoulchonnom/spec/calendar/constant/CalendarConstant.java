package com.seoulchonnom.spec.calendar.constant;

import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CalendarConstant {
	public static final String CALENDAR_NOT_FOUND_ERROR_MESSAGE = "해당 캘린더가 없습니다.";
	public static final Pattern COLOR_HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
}
