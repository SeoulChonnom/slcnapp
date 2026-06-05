package com.seoulchonnom.spec.common.exception;

import static com.seoulchonnom.spec.calendar.constant.CalendarConstant.*;
import static com.seoulchonnom.spec.file.constant.FileConstant.*;
import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;
import static com.seoulchonnom.spec.trip.constant.TripConstant.*;
import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
	PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "허용 가능한 요청 크기를 초과했습니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),

	INVALID_USER(HttpStatus.BAD_REQUEST, USERNAME_NOT_FOUND_ERROR_MESSAGE),
	INVALID_ACCESS_TOKEN(HttpStatus.INTERNAL_SERVER_ERROR, ACCESS_TOKEN_INVALID_ERROR_MESSAGE),
	INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, REFRESH_TOKEN_INVALID_ERROR_MESSAGE),
	USER_LOGIN_FAIL_COUNT_OVER(HttpStatus.BAD_REQUEST, USER_LOGIN_FAIL_COUNT_OVER_ERROR_MESSAGE),
	USER_LOGIN_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, USER_LOGIN_NOT_FOUND_ERROR_MESSAGE),
	ACCESS_ROLE_MISSING(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	ACCESS_ROLE_DENIED(HttpStatus.FORBIDDEN, ACCESS_ROLE_DENIED_ERROR_MESSAGE),

	INVALID_SCHEDULE_DATE(HttpStatus.BAD_REQUEST, INVALID_DATE_ERROR_MESSAGE),
	SCHEDULE_NOT_FOUND(HttpStatus.BAD_REQUEST, SCHEDULE_NOT_FOND_ERROR_MESSAGE),
	CALENDAR_NOT_FOUND(HttpStatus.BAD_REQUEST, CALENDAR_NOT_FOUND_ERROR_MESSAGE),
	TRIP_NOT_FOUND(HttpStatus.BAD_REQUEST, TRIP_NOT_FOUND_ERROR_MESSAGE),

	INVALID_TRIP_REGISTER(HttpStatus.BAD_REQUEST, INVALID_TRIP_REGISTER_ERROR_MESSAGE),

	FILE_UPLOAD_FAILED(HttpStatus.BAD_REQUEST, FILE_UPLOAD_ERROR_MESSAGE),
	FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, FILE_SIZE_ERROR_MESSAGE),
	FILE_EXT_INVALID(HttpStatus.BAD_REQUEST, FILE_EXT_ERROR_MESSAGE),
	FILE_PATH_INVALID(HttpStatus.BAD_REQUEST, FILE_PATH_INVALID_ERROR_MESSAGE);

	private final HttpStatus httpStatus;
	private final String message;
}
