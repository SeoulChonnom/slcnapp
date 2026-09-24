package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;

class InspectionPropertyResourceTest {
	private static final String PROPERTY_ID = "prop-1";

	private final InspectionVisitQueryFlow inspectionVisitQueryFlow = mock(InspectionVisitQueryFlow.class);
	private final InspectionPropertyResource inspectionPropertyResource = new InspectionPropertyResource(
		inspectionVisitQueryFlow);

	@Test
	void getInspectionProperty_shouldDelegateToQueryFlowWithoutVisitId() {
		ViewedPropertyDetailRdo detail = new ViewedPropertyDetailRdo();
		when(inspectionVisitQueryFlow.getInspectionProperty(PROPERTY_ID)).thenReturn(detail);

		var response = inspectionPropertyResource.getInspectionProperty(PROPERTY_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(detail);
	}
}
