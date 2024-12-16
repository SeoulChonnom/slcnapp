package com.seoulchonnom.slcnapp.trip;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TripConstant {
	public static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

	public static final String EXT_REGEX_STRING = "jpg|png|jpeg|gif|svg";

	public static final String TRIP_NOT_FOUND_ERROR_MESSAGE = "해당 일자 나들이가 없습니다.";

	public static final String RETRIEVE_TRIP_LIST_SUCCESS_MESSAGE = "나들이 리스트 조회에 성공하였습니다.";
	public static final String RETRIEVE_TRIP_INFO_SUCCESS_MESSAGE = "나들이 상세정보 조회에 성공하였습니다.";
	public static final String RETRIEVE_FILE_SUCCESS_MESSAGE = "파일 조회에 성공하였습니다.";

	public static final String REGISTER_TRIP_SUCCESS_MESSAGE = "나들이 생성에 성공하였습니다.";

	public static final String TRIP_FILE_UPLOAD_ERROR_MESSAGE = "파일 업로드가 실패하였습니다.";
	public static final String TRIP_FILE_SIZE_ERROR_MESSAGE = "파일 사이즈가 너무 큽니다.";
	public static final String TRIP_FILE_EXT_ERROR_MESSAGE = "JPG, PNG 파일만 업로드 가능합니다.";

	public static final String FILE_PATH_INVALID_ERROR_MESSAGE = "파일 경로가 올바르지 않습니다.";
}
