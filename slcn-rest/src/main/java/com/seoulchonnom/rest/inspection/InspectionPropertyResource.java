package com.seoulchonnom.rest.inspection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.spec.inspection.facade.InspectionPropertyFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;

import lombok.RequiredArgsConstructor;

/**
 * visitId 없이 매물 단건을 조회한다. FE 라우트가 /{device}/inspection/:areaId/property/:propertyId로
 * 확정되어 링크 직행·새로고침에서 visitId를 모르기 때문이다.
 * 응답은 GET /inspection-visits/{visitId}/properties/{propertyId}와 같은 ViewedPropertyDetailRdo다.
 */
@RestController
@RequestMapping("/inspection-properties")
@RequiredArgsConstructor
public class InspectionPropertyResource implements InspectionPropertyFacade {
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow;

	@Override
	@GetMapping("/{propertyId}")
	public ResponseEntity<ViewedPropertyDetailRdo> getInspectionProperty(
		@PathVariable("propertyId") String propertyId) {
		return new ResponseEntity<>(inspectionVisitQueryFlow.getInspectionProperty(propertyId), HttpStatus.OK);
	}
}
