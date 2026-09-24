package com.seoulchonnom.rest.inspection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.aggregate.flow.inspection.ViewedPropertyFlow;
import com.seoulchonnom.spec.inspection.entity.vo.ComplexNameScope;
import com.seoulchonnom.spec.inspection.facade.ViewedPropertyFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerBulkUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inspection-visits/{visitId}")
@RequiredArgsConstructor
public class ViewedPropertyResource implements ViewedPropertyFacade {
	private final ViewedPropertyFlow viewedPropertyFlow;
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow;

	@Override
	@PostMapping("/properties")
	public ResponseEntity<ViewedPropertyDetailRdo> registerViewedProperty(@PathVariable("visitId") String visitId,
		@RequestBody ViewedPropertyCdo viewedPropertyCdo) {
		String propertyId = viewedPropertyFlow.registerViewedProperty(visitId, viewedPropertyCdo).getId();
		return new ResponseEntity<>(inspectionVisitQueryFlow.getViewedProperty(visitId, propertyId), HttpStatus.OK);
	}

	@Override
	@GetMapping("/properties/{propertyId}")
	public ResponseEntity<ViewedPropertyDetailRdo> getViewedProperty(@PathVariable("visitId") String visitId,
		@PathVariable("propertyId") String propertyId) {
		return new ResponseEntity<>(inspectionVisitQueryFlow.getViewedProperty(visitId, propertyId), HttpStatus.OK);
	}

	@Override
	@GetMapping("/complex-names")
	public ResponseEntity<List<String>> getComplexNames(@PathVariable("visitId") String visitId,
		@RequestParam(value = "scope", defaultValue = "VISIT") ComplexNameScope scope) {
		return new ResponseEntity<>(viewedPropertyFlow.getComplexNames(visitId, scope), HttpStatus.OK);
	}

	@Override
	@PutMapping("/properties/{propertyId}")
	public ResponseEntity<ViewedPropertyDetailRdo> modifyViewedProperty(@PathVariable("visitId") String visitId,
		@PathVariable("propertyId") String propertyId, @RequestBody ViewedPropertyUdo viewedPropertyUdo) {
		viewedPropertyFlow.modifyViewedProperty(visitId, propertyId, viewedPropertyUdo);
		return new ResponseEntity<>(inspectionVisitQueryFlow.getViewedProperty(visitId, propertyId), HttpStatus.OK);
	}

	@Override
	@PutMapping("/properties/{propertyId}/answers")
	public ResponseEntity<ViewedPropertyDetailRdo> modifyPropertyAnswers(@PathVariable("visitId") String visitId,
		@PathVariable("propertyId") String propertyId, @RequestBody PropertyAnswerBulkUdo propertyAnswerBulkUdo) {
		viewedPropertyFlow.modifyPropertyAnswers(visitId, propertyId, propertyAnswerBulkUdo);
		return new ResponseEntity<>(inspectionVisitQueryFlow.getViewedProperty(visitId, propertyId), HttpStatus.OK);
	}

	@Override
	@PatchMapping("/properties/{propertyId}/status")
	public ResponseEntity<ViewedPropertyDetailRdo> changeViewedPropertyStatus(
		@PathVariable("visitId") String visitId, @PathVariable("propertyId") String propertyId,
		@RequestBody ViewedPropertyStatusUdo viewedPropertyStatusUdo) {
		viewedPropertyFlow.changeViewedPropertyStatus(visitId, propertyId, viewedPropertyStatusUdo.getStatus());
		return new ResponseEntity<>(inspectionVisitQueryFlow.getViewedProperty(visitId, propertyId), HttpStatus.OK);
	}

	@Override
	@DeleteMapping("/properties/{propertyId}")
	public ResponseEntity<Void> deleteViewedProperty(@PathVariable("visitId") String visitId,
		@PathVariable("propertyId") String propertyId) {
		viewedPropertyFlow.deleteViewedProperty(visitId, propertyId);
		return ResponseEntity.noContent().build();
	}
}
