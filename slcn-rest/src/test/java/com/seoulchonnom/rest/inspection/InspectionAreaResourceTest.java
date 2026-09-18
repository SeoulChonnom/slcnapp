package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.seoulchonnom.aggregate.flow.inspection.InspectionAreaFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionAreaQueryFlow;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;

class InspectionAreaResourceTest {
	private final InspectionAreaQueryFlow inspectionAreaQueryFlow = mock(InspectionAreaQueryFlow.class);
	private final InspectionAreaFlow inspectionAreaFlow = mock(InspectionAreaFlow.class);
	private final InspectionAreaResource inspectionAreaResource = new InspectionAreaResource(
		inspectionAreaQueryFlow, inspectionAreaFlow);

	@Test
	void getInspectionAreas_shouldPassKeyword() {
		when(inspectionAreaQueryFlow.getInspectionAreas("성수")).thenReturn(List.of());

		inspectionAreaResource.getInspectionAreas("성수");

		verify(inspectionAreaQueryFlow).getInspectionAreas("성수");
	}

	@Test
	void getInspectionArea_shouldPassVisitIdAndIncludeProperties() {
		when(inspectionAreaQueryFlow.getInspectionArea("INSPECTION_AREA-0001", "v1", false))
			.thenReturn(new InspectionAreaDetailRdo());

		inspectionAreaResource.getInspectionArea("INSPECTION_AREA-0001", "v1", false);

		verify(inspectionAreaQueryFlow).getInspectionArea("INSPECTION_AREA-0001", "v1", false);
	}

	@Test
	void registerInspectionArea_shouldDelegateToFlow() {
		InspectionAreaCdo cdo = new InspectionAreaCdo("성수동", null);
		InspectionAreaRdo rdo = new InspectionAreaRdo();
		when(inspectionAreaFlow.registerInspectionArea(cdo)).thenReturn(rdo);

		assertThat(inspectionAreaResource.registerInspectionArea(cdo).getBody()).isSameAs(rdo);
	}

	@Test
	void modifyInspectionArea_shouldDelegateToFlow() {
		InspectionAreaUdo udo = new InspectionAreaUdo("성수동", "설명");
		when(inspectionAreaFlow.modifyInspectionArea("INSPECTION_AREA-0001", udo))
			.thenReturn(new InspectionAreaRdo());

		inspectionAreaResource.modifyInspectionArea("INSPECTION_AREA-0001", udo);

		verify(inspectionAreaFlow).modifyInspectionArea("INSPECTION_AREA-0001", udo);
	}

	@Test
	void deleteInspectionArea_shouldReturnNoContent() {
		var response = inspectionAreaResource.deleteInspectionArea("INSPECTION_AREA-0001");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(inspectionAreaFlow).deleteInspectionArea("INSPECTION_AREA-0001");
	}
}
