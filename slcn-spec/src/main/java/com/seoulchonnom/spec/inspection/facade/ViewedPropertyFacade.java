package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.entity.vo.ComplexNameScope;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerBulkUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;

public interface ViewedPropertyFacade {
	/**
	 * DRAFT로 만들고 활성 질문의 답변 행을 그 자리에서 생성한다(문답 스냅샷).
	 */
	ResponseEntity<ViewedPropertyDetailRdo> registerViewedProperty(String visitId,
		ViewedPropertyCdo viewedPropertyCdo);

	ResponseEntity<ViewedPropertyDetailRdo> getViewedProperty(String visitId, String propertyId);

	/**
	 * 단지/건물명 자동완성 후보. 자유 입력의 표기 흔들림을 줄인다. 전역 단지명 마스터는 만들지 않는다.
	 *
	 * scope=VISIT(기본값)은 이 임장에서 이미 쓴 이름만 준다(하위호환). scope=AREA는 이 임장이
	 * 속한 지역의 모든 회차에서 쓴 이름을 준다 — "같은 이름이어야 회차 간 매물이 연결됩니다"가
	 * 자동완성의 목적이라, 이 임장이 첫 회차라 이름이 하나도 없을 때 과거 회차의 이름이 후보가 된다.
	 */
	ResponseEntity<List<String>> getComplexNames(String visitId, ComplexNameScope scope);

	ResponseEntity<ViewedPropertyDetailRdo> modifyViewedProperty(String visitId, String propertyId,
		ViewedPropertyUdo viewedPropertyUdo);

	/**
	 * 값 컬럼만 갱신한다. 요청에 없는 questionId의 답변 행은 그대로 둔다.
	 * COMPLETED 매물이면 저장 후 완료 조건을 다시 검증한다 — 필수 답변을 비워 두고
	 * COMPLETED로 남는 상태를 만들지 않기 위해서다.
	 */
	ResponseEntity<ViewedPropertyDetailRdo> modifyPropertyAnswers(String visitId, String propertyId,
		PropertyAnswerBulkUdo propertyAnswerBulkUdo);

	ResponseEntity<ViewedPropertyDetailRdo> changeViewedPropertyStatus(String visitId, String propertyId,
		ViewedPropertyStatusUdo viewedPropertyStatusUdo);

	ResponseEntity<Void> deleteViewedProperty(String visitId, String propertyId);
}
