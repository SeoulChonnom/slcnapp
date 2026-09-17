package com.seoulchonnom.spec.inspection.constant;

public final class InspectionConstant {
	public static final String INSPECTION_AREA_NOT_FOUND_ERROR_MESSAGE = "임장 지역을 찾을 수 없습니다.";
	public static final String INSPECTION_AREA_IN_USE_ERROR_MESSAGE = "임장 기록이 있는 지역은 삭제할 수 없습니다.";
	public static final String INSPECTION_AREA_DUPLICATED_ERROR_MESSAGE = "같은 이름의 임장 지역이 이미 있습니다.";

	public static final String INSPECTION_VISIT_NOT_FOUND_ERROR_MESSAGE = "임장 기록을 찾을 수 없습니다.";
	public static final String INVALID_INSPECTION_VISIT_ERROR_MESSAGE = "임장 기록의 입력값 또는 완료 조건이 올바르지 않습니다.";

	public static final String VIEWED_PROPERTY_NOT_FOUND_ERROR_MESSAGE = "확인 매물을 찾을 수 없습니다.";
	public static final String INVALID_VIEWED_PROPERTY_ERROR_MESSAGE = "매물의 입력값 또는 완료 조건이 올바르지 않습니다.";

	public static final String INSPECTION_QUESTION_NOT_FOUND_ERROR_MESSAGE = "임장 질문을 찾을 수 없습니다.";
	public static final String INVALID_INSPECTION_QUESTION_ERROR_MESSAGE = "허용되지 않은 질문 수정입니다.";
	public static final String INSPECTION_QUESTION_CONFLICT_ERROR_MESSAGE = "질문이 이미 수정되었습니다. 새로고침 후 다시 시도하세요.";

	public static final String INSPECTION_ANSWER_REQUIRED_ERROR_MESSAGE = "필수 문답이 완료되지 않았습니다.";
	public static final String INVALID_PROPERTY_ANSWER_ERROR_MESSAGE = "문답 값이 질문 타입과 맞지 않습니다.";

	public static final String INVALID_INSPECTION_FILE_ERROR_MESSAGE = "임장 사진 정보가 올바르지 않습니다.";
	public static final String INVALID_INSPECTION_ORDER_ERROR_MESSAGE = "정렬 대상이 해당 임장에 속하지 않습니다.";

	private InspectionConstant() {
	}
}
