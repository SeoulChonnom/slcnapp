package com.seoulchonnom.rest.inspection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.spec.inspection.facade.InspectionTagFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionTagRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inspection-tags")
@RequiredArgsConstructor
public class InspectionTagResource implements InspectionTagFacade {
	private final InspectionTagLogic inspectionTagLogic;

	@Override
	@GetMapping
	public ResponseEntity<List<InspectionTagRdo>> getInspectionTags(
		@RequestParam(value = "keyword", required = false) String keyword) {
		return new ResponseEntity<>(inspectionTagLogic.getInspectionTags(keyword), HttpStatus.OK);
	}
}
