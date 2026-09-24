package com.seoulchonnom.rest.inspection;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.aggregate.flow.inspection.ViewedPropertyFlow;
import com.seoulchonnom.spec.common.response.PageRdo;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.InspectionVisitFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.FileBoxItemOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyOrderUdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inspection-visits")
@RequiredArgsConstructor
public class InspectionVisitResource implements InspectionVisitFacade {
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow;
	private final InspectionVisitFlow inspectionVisitFlow;
	private final ViewedPropertyFlow viewedPropertyFlow;

	@Override
	@GetMapping
	public ResponseEntity<PageRdo<InspectionVisitRdo>> getInspectionVisits(
		@RequestParam(value = "areaId", required = false) String areaId,
		@RequestParam(value = "status", required = false) InspectionStatus status,
		@RequestParam(value = "revisitIntent", required = false) RevisitIntent revisitIntent,
		@RequestParam(value = "tag", required = false) List<String> tags,
		@RequestParam(value = "from", required = false) String from,
		@RequestParam(value = "to", required = false) String to,
		@RequestParam(value = "page", defaultValue = "0") int page,
		@RequestParam(value = "size", defaultValue = "20") int size) {
		return new ResponseEntity<>(inspectionVisitQueryFlow.getInspectionVisits(areaId, status, revisitIntent, tags,
			parseDateTime(from, "from"), parseDateTime(to, "to"), page, size), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{visitId}")
	public ResponseEntity<InspectionVisitDetailRdo> getInspectionVisit(@PathVariable("visitId") String visitId) {
		return new ResponseEntity<>(inspectionVisitQueryFlow.getInspectionVisit(visitId), HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<InspectionVisitDetailRdo> registerInspectionVisit(
		@RequestBody InspectionVisitCdo inspectionVisitCdo) {
		String visitId = inspectionVisitFlow.registerInspectionVisit(inspectionVisitCdo).getId();
		return new ResponseEntity<>(inspectionVisitQueryFlow.getInspectionVisit(visitId), HttpStatus.OK);
	}

	@Override
	@PutMapping("/{visitId}")
	public ResponseEntity<InspectionVisitDetailRdo> modifyInspectionVisit(@PathVariable("visitId") String visitId,
		@RequestBody InspectionVisitUdo inspectionVisitUdo) {
		inspectionVisitFlow.modifyInspectionVisit(visitId, inspectionVisitUdo);
		return new ResponseEntity<>(inspectionVisitQueryFlow.getInspectionVisit(visitId), HttpStatus.OK);
	}

	@Override
	@PatchMapping("/{visitId}/status")
	public ResponseEntity<InspectionVisitDetailRdo> changeInspectionVisitStatus(
		@PathVariable("visitId") String visitId, @RequestBody InspectionVisitStatusUdo inspectionVisitStatusUdo) {
		inspectionVisitFlow.changeInspectionVisitStatus(visitId, inspectionVisitStatusUdo.getStatus());
		return new ResponseEntity<>(inspectionVisitQueryFlow.getInspectionVisit(visitId), HttpStatus.OK);
	}

	@Override
	@PutMapping("/{visitId}/properties/order")
	public ResponseEntity<Void> modifyViewedPropertyOrder(@PathVariable("visitId") String visitId,
		@RequestBody List<ViewedPropertyOrderUdo> orders) {
		viewedPropertyFlow.modifyViewedPropertyOrder(visitId, orders);
		return ResponseEntity.noContent().build();
	}

	@Override
	@PutMapping("/{visitId}/images/order")
	public ResponseEntity<Void> modifyInspectionImageOrder(@PathVariable("visitId") String visitId,
		@RequestBody List<FileBoxItemOrderUdo> orders) {
		inspectionVisitFlow.modifyInspectionImageOrder(visitId, orders);
		return ResponseEntity.noContent().build();
	}

	@Override
	@DeleteMapping("/{visitId}")
	public ResponseEntity<Void> deleteInspectionVisit(@PathVariable("visitId") String visitId) {
		inspectionVisitFlow.deleteInspectionVisit(visitId);
		return ResponseEntity.noContent().build();
	}

	private LocalDateTime parseDateTime(String value, String field) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return LocalDateTime.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw new BadRequestException(field + " 형식이 올바르지 않습니다. 예: 2026-09-17T00:00:00");
		}
	}
}
