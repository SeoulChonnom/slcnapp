package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.seoulchonnom.aggregate.flow.inspection.InspectionQuestionQueryFlow;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionPolicyUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionStatusUdo;

class InspectionQuestionResourceTest {
	private final InspectionQuestionQueryFlow inspectionQuestionQueryFlow =
		mock(InspectionQuestionQueryFlow.class);
	private final InspectionQuestionLogic inspectionQuestionLogic = mock(InspectionQuestionLogic.class);
	private final InspectionQuestionResource inspectionQuestionResource = new InspectionQuestionResource(
		inspectionQuestionQueryFlow, inspectionQuestionLogic);

	@Test
	void getInspectionQuestions_shouldDefaultToEnabledOnlyWithoutAnswerCount() {
		when(inspectionQuestionQueryFlow.getInspectionQuestions(false, false)).thenReturn(List.of());

		var response = inspectionQuestionResource.getInspectionQuestions(false, false);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(inspectionQuestionQueryFlow).getInspectionQuestions(false, false);
	}

	@Test
	void getInspectionQuestionVersions_shouldDelegateToQueryFlow() {
		when(inspectionQuestionQueryFlow.getInspectionQuestionVersions("q1")).thenReturn(List.of());

		inspectionQuestionResource.getInspectionQuestionVersions("q1");

		verify(inspectionQuestionQueryFlow).getInspectionQuestionVersions("q1");
	}

	@Test
	void registerInspectionQuestion_shouldDelegateToLogic() {
		InspectionQuestionCdo cdo = new InspectionQuestionCdo();
		InspectionQuestionRdo rdo = new InspectionQuestionRdo();
		when(inspectionQuestionLogic.registerInspectionQuestion(cdo)).thenReturn(rdo);

		assertThat(inspectionQuestionResource.registerInspectionQuestion(cdo).getBody()).isSameAs(rdo);
	}

	@Test
	void modifyInspectionQuestionContent_shouldDelegateToLogic() {
		InspectionQuestionContentUdo udo = new InspectionQuestionContentUdo();
		when(inspectionQuestionLogic.modifyInspectionQuestionContent("q1", udo))
			.thenReturn(new InspectionQuestionRdo());

		inspectionQuestionResource.modifyInspectionQuestionContent("q1", udo);

		verify(inspectionQuestionLogic).modifyInspectionQuestionContent("q1", udo);
	}

	@Test
	void modifyInspectionQuestionPolicyAndStatus_shouldDelegateToLogic() {
		InspectionQuestionPolicyUdo policyUdo = new InspectionQuestionPolicyUdo(false, 3);
		InspectionQuestionStatusUdo statusUdo = new InspectionQuestionStatusUdo(false);
		when(inspectionQuestionLogic.modifyInspectionQuestionPolicy("q1", policyUdo))
			.thenReturn(new InspectionQuestionRdo());
		when(inspectionQuestionLogic.changeInspectionQuestionStatus("q1", statusUdo))
			.thenReturn(new InspectionQuestionRdo());

		inspectionQuestionResource.modifyInspectionQuestionPolicy("q1", policyUdo);
		inspectionQuestionResource.changeInspectionQuestionStatus("q1", statusUdo);

		verify(inspectionQuestionLogic).modifyInspectionQuestionPolicy("q1", policyUdo);
		verify(inspectionQuestionLogic).changeInspectionQuestionStatus("q1", statusUdo);
	}

	@Test
	void modifyInspectionQuestionOrder_shouldReturnNoContent() {
		List<InspectionQuestionOrderUdo> orders = List.of(new InspectionQuestionOrderUdo("q1", 2));

		var response = inspectionQuestionResource.modifyInspectionQuestionOrder(orders);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(inspectionQuestionLogic).modifyInspectionQuestionOrder(orders);
	}
}
