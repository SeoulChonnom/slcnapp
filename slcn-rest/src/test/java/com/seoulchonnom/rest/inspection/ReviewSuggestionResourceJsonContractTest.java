package com.seoulchonnom.rest.inspection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.seoulchonnom.aggregate.flow.inspection.InspectionReviewSuggestionFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.aggregate.flow.inspection.ViewedPropertyFlow;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.inspection.facade.sdo.ReviewSuggestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ReviewSuggestionSdo;

/**
 * 후기 제안 요청의 Bean Validation이 VALIDATION_FAILED(errors[].field/code)로 내려가는지 확인한다.
 */
class ReviewSuggestionResourceJsonContractTest {
	private static final String VISIT_URL = "/inspection-visits/visit-1/review-suggestion";
	private static final String PROPERTY_URL = "/inspection-visits/visit-1/properties/prop-1/review-suggestion";

	private InspectionReviewSuggestionFlow flow;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		flow = mock(InspectionReviewSuggestionFlow.class);
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		InspectionVisitResource visitResource = new InspectionVisitResource(mock(InspectionVisitQueryFlow.class),
			mock(InspectionVisitFlow.class), mock(ViewedPropertyFlow.class), flow);
		ViewedPropertyResource propertyResource = new ViewedPropertyResource(mock(ViewedPropertyFlow.class),
			mock(InspectionVisitQueryFlow.class), flow);
		mockMvc = MockMvcBuilders.standaloneSetup(visitResource, propertyResource)
			.setControllerAdvice(new CommonExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void visitSuggestion_shouldReturnProsInResponse() throws Exception {
		when(flow.suggestVisitReview(any(), any()))
			.thenReturn(new ReviewSuggestionRdo("조용한 동네", "- 한강뷰", "", List.of("한강")));

		mockMvc.perform(post(VISIT_URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"한강이 보임\",\"pros\":\"조용함\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.oneLineReview").value("조용한 동네"))
			.andExpect(jsonPath("$.pros").value("- 한강뷰"))
			.andExpect(jsonPath("$.tags[0]").value("한강"));

		ReviewSuggestionSdo expected = new ReviewSuggestionSdo("한강이 보임", "조용함");
		verify(flow).suggestVisitReview(org.mockito.ArgumentMatchers.eq("visit-1"),
			org.mockito.ArgumentMatchers.argThat(sdo -> expected.getMemo().equals(sdo.getMemo())
				&& expected.getPros().equals(sdo.getPros())));
	}

	@Test
	void propertySuggestion_shouldReturnProsInResponse() throws Exception {
		when(flow.suggestPropertyReview(any(), any(), any()))
			.thenReturn(new ReviewSuggestionRdo("채광 좋음", "- 남향", "", List.of()));

		mockMvc.perform(post(PROPERTY_URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"채광이 좋음\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pros").value("- 남향"));
	}

	@Test
	void suggestion_shouldRejectBlankMemoWithNotBlankError() throws Exception {
		for (String url : List.of(VISIT_URL, PROPERTY_URL)) {
			mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
					.content("{\"memo\":\"  \",\"pros\":\"조용함\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.title").value("메모를 입력해야 제안할 수 있습니다."))
				.andExpect(jsonPath("$.errors[0].field").value("memo"))
				.andExpect(jsonPath("$.errors[0].code").value("NOT_BLANK"));
		}
		verifyNoInteractions(flow);
	}

	@Test
	void suggestion_shouldRejectMissingMemo() throws Exception {
		mockMvc.perform(post(VISIT_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("memo"))
			.andExpect(jsonPath("$.errors[0].code").value("NOT_BLANK"));
		verifyNoInteractions(flow);
	}

	@Test
	void suggestion_shouldRejectTooLongMemoAndPros() throws Exception {
		String tooLong = "가".repeat(5001);

		mockMvc.perform(post(VISIT_URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"" + tooLong + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("memo"))
			.andExpect(jsonPath("$.errors[0].code").value("SIZE"));
		mockMvc.perform(post(PROPERTY_URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"메모\",\"pros\":\"" + tooLong + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("pros"))
			.andExpect(jsonPath("$.errors[0].code").value("SIZE"));
		verifyNoInteractions(flow);
	}

	@Test
	void suggestion_shouldRejectMissingBody() throws Exception {
		mockMvc.perform(post(VISIT_URL).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
		verifyNoInteractions(flow);
	}
}
