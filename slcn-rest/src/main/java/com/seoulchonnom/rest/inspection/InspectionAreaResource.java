package com.seoulchonnom.rest.inspection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.inspection.InspectionAreaFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionAreaQueryFlow;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionAreaSort;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.InspectionAreaFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.AreaViewedPropertyRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaListRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inspection-areas")
@RequiredArgsConstructor
public class InspectionAreaResource implements InspectionAreaFacade {
	private final InspectionAreaQueryFlow inspectionAreaQueryFlow;
	private final InspectionAreaFlow inspectionAreaFlow;

	@Override
	@GetMapping
	public ResponseEntity<InspectionAreaListRdo> getInspectionAreas(
		@RequestParam(value = "keyword", required = false) String keyword,
		@RequestParam(value = "revisitIntent", required = false) RevisitIntent revisitIntent,
		@RequestParam(value = "sort", required = false) InspectionAreaSort sort,
		@RequestParam(value = "page", defaultValue = "0") int page,
		@RequestParam(value = "size", defaultValue = "20") int size) {
		return new ResponseEntity<>(inspectionAreaQueryFlow.getInspectionAreas(keyword, revisitIntent, sort, page,
			size), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{areaId}")
	public ResponseEntity<InspectionAreaDetailRdo> getInspectionArea(@PathVariable("areaId") String areaId,
		@RequestParam(value = "visitId", required = false) String visitId,
		@RequestParam(value = "includeProperties", defaultValue = "true") boolean includeProperties) {
		return new ResponseEntity<>(inspectionAreaQueryFlow.getInspectionArea(areaId, visitId, includeProperties),
			HttpStatus.OK);
	}

	@Override
	@GetMapping("/{areaId}/properties")
	public ResponseEntity<List<AreaViewedPropertyRdo>> getAreaProperties(@PathVariable("areaId") String areaId,
		@RequestParam(value = "complexName", required = false) String complexName,
		@RequestParam(value = "name", required = false) String name) {
		return new ResponseEntity<>(inspectionAreaQueryFlow.getAreaProperties(areaId, complexName, name),
			HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<InspectionAreaRdo> registerInspectionArea(
		@RequestBody InspectionAreaCdo inspectionAreaCdo) {
		return new ResponseEntity<>(inspectionAreaFlow.registerInspectionArea(inspectionAreaCdo), HttpStatus.OK);
	}

	@Override
	@PutMapping("/{areaId}")
	public ResponseEntity<InspectionAreaRdo> modifyInspectionArea(@PathVariable("areaId") String areaId,
		@RequestBody InspectionAreaUdo inspectionAreaUdo) {
		return new ResponseEntity<>(inspectionAreaFlow.modifyInspectionArea(areaId, inspectionAreaUdo),
			HttpStatus.OK);
	}

	@Override
	@DeleteMapping("/{areaId}")
	public ResponseEntity<Void> deleteInspectionArea(@PathVariable("areaId") String areaId) {
		inspectionAreaFlow.deleteInspectionArea(areaId);
		return ResponseEntity.noContent().build();
	}
}
