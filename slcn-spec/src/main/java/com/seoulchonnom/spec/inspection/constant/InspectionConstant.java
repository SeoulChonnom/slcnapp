package com.seoulchonnom.spec.inspection.constant;

public final class InspectionConstant {
	public static final String INSPECTION_AREA_NOT_FOUND_ERROR_MESSAGE = "임장 지역을 찾을 수 없습니다.";
	public static final String INSPECTION_AREA_IN_USE_ERROR_MESSAGE = "임장 기록이 있는 지역은 삭제할 수 없습니다.";
	public static final String INSPECTION_AREA_DUPLICATED_ERROR_MESSAGE = "같은 이름의 임장 지역이 이미 있습니다.";

	public static final String INSPECTION_VISIT_NOT_FOUND_ERROR_MESSAGE = "임장 기록을 찾을 수 없습니다.";
	public static final String INVALID_INSPECTION_VISIT_ERROR_MESSAGE = "임장 기록의 입력값 또는 완료 조건이 올바르지 않습니다.";
	public static final String INSPECTION_VISIT_CONFLICT_ERROR_MESSAGE = "임장 기록이 이미 수정되었습니다. 새로고침 후 다시 시도하세요.";

	public static final String VIEWED_PROPERTY_NOT_FOUND_ERROR_MESSAGE = "확인 매물을 찾을 수 없습니다.";
	public static final String INVALID_VIEWED_PROPERTY_ERROR_MESSAGE = "매물의 입력값 또는 완료 조건이 올바르지 않습니다.";
	public static final String VIEWED_PROPERTY_CONFLICT_ERROR_MESSAGE = "매물 정보가 이미 수정되었습니다. 새로고침 후 다시 시도하세요.";

	public static final String INSPECTION_QUESTION_NOT_FOUND_ERROR_MESSAGE = "임장 질문을 찾을 수 없습니다.";
	public static final String INVALID_INSPECTION_QUESTION_ERROR_MESSAGE = "허용되지 않은 질문 수정입니다.";
	public static final String INSPECTION_QUESTION_CONFLICT_ERROR_MESSAGE = "질문이 이미 수정되었습니다. 새로고침 후 다시 시도하세요.";

	public static final String INSPECTION_QUESTION_CATEGORY_NOT_FOUND_ERROR_MESSAGE = "질문 분류를 찾을 수 없습니다.";
	public static final String INVALID_INSPECTION_QUESTION_CATEGORY_ERROR_MESSAGE = "허용되지 않은 분류 입력입니다.";
	public static final String INSPECTION_QUESTION_CATEGORY_DUPLICATED_ERROR_MESSAGE = "같은 이름의 질문 분류가 이미 있습니다.";
	public static final String INSPECTION_QUESTION_CATEGORY_IN_USE_ERROR_MESSAGE = "활성 질문이 있는 분류는 비활성화할 수 없습니다.";
	public static final String INSPECTION_QUESTION_CATEGORY_CONFLICT_ERROR_MESSAGE = "분류가 이미 수정되었습니다. 새로고침 후 다시 시도하세요.";
	public static final String INSPECTION_QUESTION_CATEGORY_REQUIRED_ERROR_MESSAGE = "질문 분류는 필수입니다.";
	public static final String INSPECTION_QUESTION_CATEGORY_DISABLED_ERROR_MESSAGE = "비활성화된 분류에는 질문을 등록하거나 옮길 수 없습니다.";
	public static final String INSPECTION_QUESTION_REACTIVATION_BLOCKED_ERROR_MESSAGE = "비활성화된 분류의 질문은 다시 활성화할 수 없습니다.";

	public static final String INSPECTION_ANSWER_REQUIRED_ERROR_MESSAGE = "필수 문답이 완료되지 않았습니다.";
	public static final String INVALID_PROPERTY_ANSWER_ERROR_MESSAGE = "문답 값이 질문 타입과 맞지 않습니다.";

	public static final String INVALID_INSPECTION_FILE_ERROR_MESSAGE = "임장 사진 정보가 올바르지 않습니다.";
	public static final String INVALID_INSPECTION_ORDER_ERROR_MESSAGE = "정렬 대상이 해당 임장에 속하지 않습니다.";

	public static final String REVIEW_SUGGESTION_UNAVAILABLE_ERROR_MESSAGE = "후기 제안을 지금은 사용할 수 없습니다. 잠시 후 다시 시도하세요.";
	public static final String REVIEW_SUGGESTION_MISCONFIGURED_ERROR_MESSAGE = "AI 후기 제안 설정에 문제가 있어 사용할 수 없습니다. 관리자에게 문의하세요.";

	public static final String ADDRESS_LOOKUP_UNAVAILABLE_ERROR_MESSAGE = "주소 검색 설정에 문제가 있어 사용할 수 없습니다. 관리자에게 문의하세요.";
	public static final String ADDRESS_LOOKUP_FAILED_ERROR_MESSAGE = "주소 검색 서비스에서 응답을 받지 못했습니다. 잠시 후 다시 시도하세요.";
	public static final String INVALID_ADDRESS_KEYWORD_ERROR_MESSAGE = "검색어가 올바르지 않습니다.";

	public static final String WALKING_ROUTE_UNAVAILABLE_ERROR_MESSAGE = "도보 경로 설정에 문제가 있어 사용할 수 없습니다. 관리자에게 문의하세요.";
	public static final String WALKING_ROUTE_QUOTA_EXCEEDED_ERROR_MESSAGE = "도보 경로 조회 한도를 초과했습니다. 내일 다시 시도하세요.";
	public static final String WALKING_ROUTE_FAILED_ERROR_MESSAGE = "도보 경로 서비스에서 경로를 받지 못했습니다. 잠시 후 다시 시도하세요.";

	private InspectionConstant() {
	}
}
