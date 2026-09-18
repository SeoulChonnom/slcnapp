package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.aggregate.flow.inspection.ViewedPropertyFlow;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.FileBoxItemOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyOrderUdo;

class InspectionVisitResourceTest {
	private static final String VISIT_ID = "INSPECTION_VISIT-0001";

	private final InspectionVisitQueryFlow inspectionVisitQueryFlow = mock(InspectionVisitQueryFlow.class);
	private final InspectionVisitFlow inspectionVisitFlow = mock(InspectionVisitFlow.class);
	private final ViewedPropertyFlow viewedPropertyFlow = mock(ViewedPropertyFlow.class);
	private final InspectionVisitResource inspectionVisitResource = new InspectionVisitResource(
		inspectionVisitQueryFlow, inspectionVisitFlow, viewedPropertyFlow);

	private InspectionVisit savedVisit() {
		InspectionVisit visit = new InspectionVisit(VISIT_ID, "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
		return visit;
	}

	@Test
	void getInspectionVisits_shouldPassFiltersThrough() {
		when(inspectionVisitQueryFlow.getInspectionVisits(any(), any(), any(), any(), any(), any()))
			.thenReturn(List.of());

		inspectionVisitResource.getInspectionVisits("INSPECTION_AREA-0001", InspectionStatus.DRAFT, null,
			List.of("한강"), "2026-09-01T00:00:00", "2026-09-30T23:59:59");

		verify(inspectionVisitQueryFlow).getInspectionVisits("INSPECTION_AREA-0001", InspectionStatus.DRAFT, null,
			List.of("한강"), LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59, 59));
	}

	@Test
	void getInspectionVisits_shouldRejectMalformedDateRange() {
		assertThatThrownBy(() -> inspectionVisitResource.getInspectionVisits(null, null, null, null, "2026-09-01",
			null))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void getInspectionVisits_shouldTreatBlankRangeAsAbsent() {
		when(inspectionVisitQueryFlow.getInspectionVisits(any(), any(), any(), any(), any(), any()))
			.thenReturn(List.of());

		inspectionVisitResource.getInspectionVisits(null, null, null, null, "  ", null);

		verify(inspectionVisitQueryFlow).getInspectionVisits(null, null, null, null, null, null);
	}

	@Test
	void registerInspectionVisit_shouldReturnDetailBuiltByQueryFlow() {
		InspectionVisitCdo cdo = new InspectionVisitCdo();
		InspectionVisitDetailRdo detail = new InspectionVisitDetailRdo();
		when(inspectionVisitFlow.registerInspectionVisit(cdo)).thenReturn(savedVisit());
		when(inspectionVisitQueryFlow.getInspectionVisit(VISIT_ID)).thenReturn(detail);

		var response = inspectionVisitResource.registerInspectionVisit(cdo);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(detail);
	}

	@Test
	void modifyInspectionVisit_shouldReturnFreshDetail() {
		InspectionVisitUdo udo = new InspectionVisitUdo();
		InspectionVisitDetailRdo detail = new InspectionVisitDetailRdo();
		when(inspectionVisitQueryFlow.getInspectionVisit(VISIT_ID)).thenReturn(detail);

		inspectionVisitResource.modifyInspectionVisit(VISIT_ID, udo);

		verify(inspectionVisitFlow).modifyInspectionVisit(VISIT_ID, udo);
		verify(inspectionVisitQueryFlow).getInspectionVisit(VISIT_ID);
	}

	@Test
	void changeInspectionVisitStatus_shouldUnwrapStatusFromBody() {
		when(inspectionVisitQueryFlow.getInspectionVisit(VISIT_ID)).thenReturn(new InspectionVisitDetailRdo());

		inspectionVisitResource.changeInspectionVisitStatus(VISIT_ID,
			new InspectionVisitStatusUdo(InspectionStatus.COMPLETED));

		verify(inspectionVisitFlow).changeInspectionVisitStatus(VISIT_ID, InspectionStatus.COMPLETED);
	}

	@Test
	void modifyViewedPropertyOrder_shouldGoToPropertyFlow() {
		List<ViewedPropertyOrderUdo> orders = List.of(new ViewedPropertyOrderUdo("p1", 2));

		var response = inspectionVisitResource.modifyViewedPropertyOrder(VISIT_ID, orders);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(viewedPropertyFlow).modifyViewedPropertyOrder(VISIT_ID, orders);
	}

	@Test
	void modifyInspectionImageOrder_shouldGoToVisitFlow() {
		List<FileBoxItemOrderUdo> orders = List.of(new FileBoxItemOrderUdo("item-1", 2));

		inspectionVisitResource.modifyInspectionImageOrder(VISIT_ID, orders);

		verify(inspectionVisitFlow).modifyInspectionImageOrder(VISIT_ID, orders);
	}

	@Test
	void deleteInspectionVisit_shouldReturnNoContent() {
		var response = inspectionVisitResource.deleteInspectionVisit(VISIT_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(inspectionVisitFlow).deleteInspectionVisit(VISIT_ID);
	}
}
