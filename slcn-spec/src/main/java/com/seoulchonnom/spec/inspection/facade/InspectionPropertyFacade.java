package com.seoulchonnom.spec.inspection.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;

/**
 * visitId 없이 매물 단건을 조회한다.
 *
 * FE 라우트가 /{device}/inspection/:areaId/property/:propertyId로 확정되어 visitId를
 * 알 수 없다 — 링크 직행이나 새로고침에서 GET /inspection-visits/{visitId}/properties/{propertyId}를
 * 쓸 수 없는 이유다. 응답 타입은 그 엔드포인트와 같은 ViewedPropertyDetailRdo를 그대로 쓴다.
 * 타입이 갈라지면 FE가 두 모양을 다뤄야 한다.
 */
public interface InspectionPropertyFacade {
	ResponseEntity<ViewedPropertyDetailRdo> getInspectionProperty(String propertyId);
}
