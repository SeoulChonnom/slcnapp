package com.seoulchonnom.spec.file.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileConstant {
	public static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

	public static final String AVAILABLE_PATH = "logo|map|travel|profile";
	/**
	 * 업로드로 받아들이는 확장자. 파생본 전용 포맷(webp)은 여기에 넣지 않는다.
	 */
	public static final String EXT_REGEX_STRING = "jpg|png|jpeg|gif|svg";
	/**
	 * 디스크에 존재할 수 있는 확장자. 원본 확장자에 파생본 인코딩 결과가 더해진다.
	 */
	private static final String STORED_EXT_REGEX_STRING = EXT_REGEX_STRING + "|webp";
	private static final String UUID_REGEX_STRING =
		"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
	/**
	 * 원본은 {uuid}.{ext}, 파생본은 {uuid}_{variant}.{ext} 형태를 갖는다.
	 */
	private static final String VARIANT_SUFFIX_REGEX_STRING = "(_[a-z][a-z0-9-]{1,30})?";
	public static final String FILE_NAME_REGEX_STRING =
		UUID_REGEX_STRING + VARIANT_SUFFIX_REGEX_STRING + "\\.(" + STORED_EXT_REGEX_STRING + ")";
	public static final String FILE_PATH_REGEX_STRING = "(" + AVAILABLE_PATH + ")/" + FILE_NAME_REGEX_STRING;

	public static final float VARIANT_COMPRESSION_QUALITY = 0.82f;

	/**
	 * 파일 ID와 저장 파일명이 불변이므로 파생본 바이트도 불변이다. 재검증 없이 하루 동안 캐시해도 안전하다.
	 */
	public static final long IMAGE_CACHE_MAX_AGE_SECONDS = 86400L;
	public static final String ORIGINAL_VARIANT_TAG = "original";

	public static final String RETRIEVE_FILE_SUCCESS_MESSAGE = "파일 조회에 성공하였습니다.";
	public static final String FILE_UPLOAD_SUCCESS_MESSAGE = "파일 업로드에 성공하였습니다.";

	public static final String FILE_UPLOAD_ERROR_MESSAGE = "파일 업로드가 실패하였습니다.";
	public static final String FILE_SIZE_ERROR_MESSAGE = "파일 사이즈가 너무 큽니다.";
	public static final String FILE_EXT_ERROR_MESSAGE = "JPG, PNG 파일만 업로드 가능합니다.";

	public static final String FILE_PATH_INVALID_ERROR_MESSAGE = "파일 경로가 올바르지 않습니다.";
}
