package com.seoulchonnom.spec.file.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileConstant {
	/**
	 * 카메라 JPG(20~40 MB)를 받을 수 있는 크기. application.yml의 multipart max-file-size와 같게 둔다.
	 */
	public static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;
	/**
	 * 헤더로 판단하는 최대 픽셀 수. 작은 파일이 거대한 해상도로 풀리는 압축 폭탄을 막는다.
	 * 40 MP 카메라 JPG의 두 배 이상이라 실제 사진은 걸리지 않는다.
	 */
	public static final long MAX_IMAGE_PIXELS = 100_000_000L;
	/**
	 * RAW는 서버를 거치지 않으므로 서버 메모리와 무관하다. 세션 파트 수와 저장 비용을 묶기 위한 상한이다.
	 */
	public static final long MAX_RAW_FILE_SIZE = 500 * 1024 * 1024L;
	/**
	 * RAW 첨부로 받는 확장자. 일반 업로드(EXT_REGEX_STRING)로는 받지 않고 직접 업로드 세션으로만 들어온다.
	 */
	public static final String RAW_EXT = "raf";
	public static final String RAW_MIME_TYPE = "image/x-fujifilm-raf";
	/**
	 * 후지필름 RAF 파일 첫 16바이트. 끝의 공백까지 포함한다.
	 */
	public static final String RAF_MAGIC = "FUJIFILMCCD-RAW ";

	public static final String AVAILABLE_PATH = "logo|map|travel|profile|inspection";
	/**
	 * 업로드로 받아들이는 확장자. 파생본 전용 포맷(webp)은 여기에 넣지 않는다.
	 */
	public static final String EXT_REGEX_STRING = "jpg|png|jpeg|gif|svg";
	/**
	 * 저장소에 존재할 수 있는 확장자. 원본 확장자에 파생본 인코딩 결과와 RAW 첨부가 더해진다.
	 * RAW가 경로 기반 이미지 조회로 나가지 않게 하는 차단은 FileLogic.getImageFile이 따로 한다.
	 */
	private static final String STORED_EXT_REGEX_STRING = EXT_REGEX_STRING + "|webp|" + RAW_EXT;
	private static final String UUID_REGEX_STRING =
		"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
	/**
	 * 원본은 {uuid}.{ext}, 파생본은 {uuid}_{variant}.{ext} 형태를 갖는다.
	 */
	private static final String VARIANT_SUFFIX_REGEX_STRING = "(_[a-z][a-z0-9-]{1,30})?";
	public static final String FILE_NAME_REGEX_STRING =
		UUID_REGEX_STRING + VARIANT_SUFFIX_REGEX_STRING + "\\.(" + STORED_EXT_REGEX_STRING + ")";
	public static final String FILE_PATH_REGEX_STRING = "(" + AVAILABLE_PATH + ")/" + FILE_NAME_REGEX_STRING;

	/**
	 * 파생본 저장 파일명. 원본과 달리 {uuid}_{variant} 접미사를 반드시 갖는다.
	 * 파일명만 받는 경로 기반 조회에서 원본과 파생본의 저장 prefix를 갈라야 하므로 별도로 둔다.
	 */
	public static final String VARIANT_FILE_NAME_REGEX_STRING =
		UUID_REGEX_STRING + "_[a-z][a-z0-9-]{1,30}\\.(" + STORED_EXT_REGEX_STRING + ")";

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
	public static final String FILE_PIXEL_ERROR_MESSAGE = "이미지 해상도가 너무 큽니다.";
	/**
	 * HEIC는 서버에서 디코딩하지 않는다. 변환은 FE가 하므로 일반 확장자 오류와 다른 안내를 준다.
	 */
	public static final String HEIC_REGEX_STRING = "heic|heif";
	public static final String FILE_HEIC_ERROR_MESSAGE = "HEIC 사진은 JPG로 변환해 올려 주세요.";

	public static final String FILE_PATH_INVALID_ERROR_MESSAGE = "파일 경로가 올바르지 않습니다.";

	public static final String RAW_UPLOAD_TYPE_ERROR_MESSAGE = "RAW 파일은 여행 사진에만 첨부할 수 있습니다.";
	public static final String RAW_UPLOAD_EXT_ERROR_MESSAGE = "RAF 파일만 RAW로 올릴 수 있습니다.";
	public static final String RAW_UPLOAD_NOT_RAW_ERROR_MESSAGE = "RAW 업로드 자산이 아닙니다.";
	public static final String RAW_UPLOAD_SESSION_MISMATCH_ERROR_MESSAGE = "업로드 세션이 일치하지 않습니다.";
	public static final String RAW_UPLOAD_PARTS_INVALID_ERROR_MESSAGE = "업로드 파트 정보가 올바르지 않습니다.";
	public static final String RAW_UPLOAD_CONTENT_INVALID_ERROR_MESSAGE = "RAW 파일 내용이 올바르지 않습니다. 다시 올려 주세요.";
	public static final String FILE_ASSET_RAW_NOT_VIEWABLE_ERROR_MESSAGE = "RAW 파일은 화면에 표시할 수 없습니다. 다운로드를 이용해 주세요.";
	public static final String RAW_UPLOAD_IN_USE_ERROR_MESSAGE = "여행에 연결된 RAW 파일은 삭제할 수 없습니다. 여행 수정에서 먼저 연결을 해제해 주세요.";
	public static final String PRESIGNED_URL_NOT_SUPPORTED_ERROR_MESSAGE = "현재 저장소 설정에서는 지원하지 않는 기능입니다.";
}
