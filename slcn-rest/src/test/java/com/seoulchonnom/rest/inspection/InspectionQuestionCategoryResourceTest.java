package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDuplicatedException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryInUseException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionCategoryLogic;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryUdo;

class InspectionQuestionCategoryResourceTest {
	private InspectionQuestionCategoryLogic inspectionQuestionCategoryLogic;
	private InspectionQuestionCategoryResource inspectionQuestionCategoryResource;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		inspectionQuestionCategoryLogic = mock(InspectionQuestionCategoryLogic.class);
		inspectionQuestionCategoryResource = new InspectionQuestionCategoryResource(inspectionQuestionCategoryLogic);
		mockMvc = MockMvcBuilders.standaloneSetup(inspectionQuestionCategoryResource)
			.setControllerAdvice(new CommonExceptionHandler())
			.build();
	}

	@Test
	void getInspectionQuestionCategories_shouldDefaultToEnabledOnly() {
		when(inspectionQuestionCategoryLogic.getInspectionQuestionCategories(false)).thenReturn(List.of());

		var response = inspectionQuestionCategoryResource.getInspectionQuestionCategories(false);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(inspectionQuestionCategoryLogic).getInspectionQuestionCategories(false);
	}

	@Test
	void registerInspectionQuestionCategory_shouldDelegateToLogic() {
		InspectionQuestionCategoryCdo cdo = new InspectionQuestionCategoryCdo("채광·환기", 1);
		InspectionQuestionCategoryRdo rdo = new InspectionQuestionCategoryRdo();
		when(inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(cdo)).thenReturn(rdo);

		assertThat(inspectionQuestionCategoryResource.registerInspectionQuestionCategory(cdo).getBody())
			.isSameAs(rdo);
	}

	@Test
	void renameInspectionQuestionCategory_shouldDelegateToLogic() {
		InspectionQuestionCategoryUdo udo = new InspectionQuestionCategoryUdo("채광");
		when(inspectionQuestionCategoryLogic.renameInspectionQuestionCategory("c1", udo))
			.thenReturn(new InspectionQuestionCategoryRdo());

		inspectionQuestionCategoryResource.renameInspectionQuestionCategory("c1", udo);

		verify(inspectionQuestionCategoryLogic).renameInspectionQuestionCategory("c1", udo);
	}

	@Test
	void changeInspectionQuestionCategoryStatus_shouldDelegateToLogic() {
		InspectionQuestionCategoryStatusUdo udo = new InspectionQuestionCategoryStatusUdo(false);
		when(inspectionQuestionCategoryLogic.changeInspectionQuestionCategoryStatus("c1", udo))
			.thenReturn(new InspectionQuestionCategoryRdo());

		inspectionQuestionCategoryResource.changeInspectionQuestionCategoryStatus("c1", udo);

		verify(inspectionQuestionCategoryLogic).changeInspectionQuestionCategoryStatus("c1", udo);
	}

	@Test
	void modifyInspectionQuestionCategoryOrder_shouldReturnNoContent() {
		List<InspectionQuestionCategoryOrderUdo> orders = List.of(new InspectionQuestionCategoryOrderUdo("c1", 2));

		var response = inspectionQuestionCategoryResource.modifyInspectionQuestionCategoryOrder(orders);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(inspectionQuestionCategoryLogic).modifyInspectionQuestionCategoryOrder(orders);
	}

	@Test
	void getInspectionQuestionCategories_shouldReturnRdoJson() throws Exception {
		InspectionQuestionCategoryRdo rdo = new InspectionQuestionCategoryRdo();
		rdo.setCategoryId("INSPECTION_QUESTION_CATEGORY-0001");
		rdo.setName("채광·환기");
		rdo.setSortOrder(1);
		rdo.setEnabled(true);
		rdo.setEnabledQuestionCount(4);
		when(inspectionQuestionCategoryLogic.getInspectionQuestionCategories(false)).thenReturn(List.of(rdo));

		mockMvc.perform(get("/inspection-question-categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].categoryId").value("INSPECTION_QUESTION_CATEGORY-0001"))
			.andExpect(jsonPath("$[0].name").value("채광·환기"))
			.andExpect(jsonPath("$[0].sortOrder").value(1))
			.andExpect(jsonPath("$[0].enabled").value(true))
			.andExpect(jsonPath("$[0].enabledQuestionCount").value(4));

		verify(inspectionQuestionCategoryLogic).getInspectionQuestionCategories(false);
	}

	@Test
	void getInspectionQuestionCategories_withIncludeDisabledTrue_shouldPassThrough() throws Exception {
		when(inspectionQuestionCategoryLogic.getInspectionQuestionCategories(true)).thenReturn(List.of());

		mockMvc.perform(get("/inspection-question-categories").param("includeDisabled", "true"))
			.andExpect(status().isOk());

		verify(inspectionQuestionCategoryLogic).getInspectionQuestionCategories(true);
	}

	@Test
	void registerInspectionQuestionCategory_shouldBindRequestBody() throws Exception {
		when(inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(any(InspectionQuestionCategoryCdo.class)))
			.thenReturn(new InspectionQuestionCategoryRdo());

		mockMvc.perform(post("/inspection-question-categories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "채광·환기", "sortOrder": 1 }
					"""))
			.andExpect(status().isOk());

		ArgumentCaptor<InspectionQuestionCategoryCdo> captor =
			ArgumentCaptor.forClass(InspectionQuestionCategoryCdo.class);
		verify(inspectionQuestionCategoryLogic).registerInspectionQuestionCategory(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("채광·환기");
		assertThat(captor.getValue().getSortOrder()).isEqualTo(1);
	}

	@Test
	void registerInspectionQuestionCategory_withDuplicateName_shouldReturnConflict() throws Exception {
		when(inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(any(InspectionQuestionCategoryCdo.class)))
			.thenThrow(new InspectionQuestionCategoryDuplicatedException());

		mockMvc.perform(post("/inspection-question-categories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "채광·환기", "sortOrder": 1 }
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSPECTION_QUESTION_CATEGORY_DUPLICATED"))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	/**
	 * 본문이 ProblemDetail과 비슷해도 우리 형식은 RFC 9457 문서가 아니다. Accept에
	 * problem+json을 넣어도 FE가 JSON으로 파싱하는 application/json으로 내려가야 한다.
	 */
	@Test
	void errorResponse_withProblemJsonAccept_shouldStillBeApplicationJson() throws Exception {
		when(inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(any(InspectionQuestionCategoryCdo.class)))
			.thenThrow(new InspectionQuestionCategoryDuplicatedException());

		mockMvc.perform(post("/inspection-question-categories")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_PROBLEM_JSON)
				.content("""
					{ "name": "채광·환기", "sortOrder": 1 }
					"""))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("INSPECTION_QUESTION_CATEGORY_DUPLICATED"));
	}

	@Test
	void registerInspectionQuestionCategory_withPlainTextBody_shouldReturnUnsupportedMediaType() throws Exception {
		mockMvc.perform(post("/inspection-question-categories")
				.contentType(MediaType.TEXT_PLAIN)
				.content("채광"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void renameInspectionQuestionCategory_withUnknownCategory_shouldReturnBadRequest() throws Exception {
		when(inspectionQuestionCategoryLogic.renameInspectionQuestionCategory(eq("missing"),
			any(InspectionQuestionCategoryUdo.class)))
			.thenThrow(new InspectionQuestionCategoryNotFoundException());

		mockMvc.perform(put("/inspection-question-categories/missing")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "채광" }
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INSPECTION_QUESTION_CATEGORY_NOT_FOUND"))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void changeInspectionQuestionCategoryStatus_withEnabledQuestions_shouldReturnConflict() throws Exception {
		when(inspectionQuestionCategoryLogic.changeInspectionQuestionCategoryStatus(eq("c1"),
			any(InspectionQuestionCategoryStatusUdo.class)))
			.thenThrow(new InspectionQuestionCategoryInUseException());

		mockMvc.perform(patch("/inspection-question-categories/c1/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "enabled": false }
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSPECTION_QUESTION_CATEGORY_IN_USE"))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	/**
	 * "/order"가 리터럴이라 "/{categoryId}"보다 우선 매칭되어야 한다. 순서가 뒤바뀌면
	 * PUT /inspection-question-categories/order가 renameInspectionQuestionCategory("order", ...)로
	 * 잘못 라우팅되어 정렬 API가 조용히 깨진다.
	 */
	@Test
	void modifyInspectionQuestionCategoryOrder_shouldNotBeShadowedByCategoryIdMapping() throws Exception {
		mockMvc.perform(put("/inspection-question-categories/order")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					[ { "categoryId": "c1", "sortOrder": 2 } ]
					"""))
			.andExpect(status().isNoContent());

		ArgumentCaptor<List<InspectionQuestionCategoryOrderUdo>> captor = ArgumentCaptor.forClass(List.class);
		verify(inspectionQuestionCategoryLogic).modifyInspectionQuestionCategoryOrder(captor.capture());
		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().get(0).getCategoryId()).isEqualTo("c1");
		verify(inspectionQuestionCategoryLogic, never()).renameInspectionQuestionCategory(eq("order"), any());
	}
}
