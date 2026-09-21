package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.aggregate.flow.inspection.ViewedPropertyFlow;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.ComplexNameScope;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerBulkUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;

class ViewedPropertyResourceTest {
	private static final String VISIT_ID = "INSPECTION_VISIT-0001";
	private static final String PROPERTY_ID = "prop-1";

	private final ViewedPropertyFlow viewedPropertyFlow = mock(ViewedPropertyFlow.class);
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow = mock(InspectionVisitQueryFlow.class);
	private final ViewedPropertyResource viewedPropertyResource = new ViewedPropertyResource(viewedPropertyFlow,
		inspectionVisitQueryFlow);

	private ViewedProperty saved() {
		ViewedProperty property = new ViewedProperty(VISIT_ID, "트리마제", "101동", 1);
		property.setId(PROPERTY_ID);
		return property;
	}

	@Test
	void registerViewedProperty_shouldReturnDetailWithSnapshot() {
		ViewedPropertyCdo cdo = new ViewedPropertyCdo();
		ViewedPropertyDetailRdo detail = new ViewedPropertyDetailRdo();
		when(viewedPropertyFlow.registerViewedProperty(VISIT_ID, cdo)).thenReturn(saved());
		when(inspectionVisitQueryFlow.getViewedProperty(VISIT_ID, PROPERTY_ID)).thenReturn(detail);

		var response = viewedPropertyResource.registerViewedProperty(VISIT_ID, cdo);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(detail);
	}

	@Test
	void getViewedProperty_shouldDelegateToQueryFlow() {
		ViewedPropertyDetailRdo detail = new ViewedPropertyDetailRdo();
		when(inspectionVisitQueryFlow.getViewedProperty(VISIT_ID, PROPERTY_ID)).thenReturn(detail);

		assertThat(viewedPropertyResource.getViewedProperty(VISIT_ID, PROPERTY_ID).getBody()).isSameAs(detail);
	}

	@Test
	void getComplexNames_shouldDelegateToFlow() {
		when(viewedPropertyFlow.getComplexNames(VISIT_ID, ComplexNameScope.VISIT)).thenReturn(List.of("트리마제"));

		assertThat(viewedPropertyResource.getComplexNames(VISIT_ID, ComplexNameScope.VISIT).getBody())
			.containsExactly("트리마제");
	}

	@Test
	void getComplexNames_shouldPassAreaScopeThrough() {
		when(viewedPropertyFlow.getComplexNames(VISIT_ID, ComplexNameScope.AREA))
			.thenReturn(List.of("갤러리아포레", "트리마제"));

		assertThat(viewedPropertyResource.getComplexNames(VISIT_ID, ComplexNameScope.AREA).getBody())
			.containsExactly("갤러리아포레", "트리마제");
	}

	@Test
	void modifyPropertyAnswers_shouldReturnFreshDetail() {
		PropertyAnswerBulkUdo udo = new PropertyAnswerBulkUdo();
		when(inspectionVisitQueryFlow.getViewedProperty(VISIT_ID, PROPERTY_ID))
			.thenReturn(new ViewedPropertyDetailRdo());

		viewedPropertyResource.modifyPropertyAnswers(VISIT_ID, PROPERTY_ID, udo);

		verify(viewedPropertyFlow).modifyPropertyAnswers(VISIT_ID, PROPERTY_ID, udo);
		verify(inspectionVisitQueryFlow).getViewedProperty(VISIT_ID, PROPERTY_ID);
	}

	@Test
	void modifyViewedProperty_shouldReturnFreshDetail() {
		ViewedPropertyUdo udo = new ViewedPropertyUdo();
		when(inspectionVisitQueryFlow.getViewedProperty(VISIT_ID, PROPERTY_ID))
			.thenReturn(new ViewedPropertyDetailRdo());

		viewedPropertyResource.modifyViewedProperty(VISIT_ID, PROPERTY_ID, udo);

		verify(viewedPropertyFlow).modifyViewedProperty(VISIT_ID, PROPERTY_ID, udo);
	}

	@Test
	void changeViewedPropertyStatus_shouldUnwrapStatusFromBody() {
		when(inspectionVisitQueryFlow.getViewedProperty(VISIT_ID, PROPERTY_ID))
			.thenReturn(new ViewedPropertyDetailRdo());

		viewedPropertyResource.changeViewedPropertyStatus(VISIT_ID, PROPERTY_ID,
			new ViewedPropertyStatusUdo(InspectionStatus.COMPLETED));

		verify(viewedPropertyFlow).changeViewedPropertyStatus(VISIT_ID, PROPERTY_ID, InspectionStatus.COMPLETED);
	}

	@Test
	void deleteViewedProperty_shouldReturnNoContent() {
		var response = viewedPropertyResource.deleteViewedProperty(VISIT_ID, PROPERTY_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(viewedPropertyFlow).deleteViewedProperty(VISIT_ID, PROPERTY_ID);
	}
}
