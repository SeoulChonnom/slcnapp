package com.seoulchonnom.boot.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import com.seoulchonnom.aggregate.flow.inspection.InspectionQuestionQueryFlow;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionCategoryLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.rest.inspection.InspectionQuestionCategoryResource;
import com.seoulchonnom.rest.inspection.InspectionQuestionResource;

/**
 * ResponseEntity<Void>는 springdoc이 본문 없는 200으로 추론한다. 실제 응답은 204이므로
 * Facade의 @ApiResponse가 문서에 반영되는지 확인한다.
 */
@WebMvcTest(
	controllers = {InspectionQuestionResource.class, InspectionQuestionCategoryResource.class},
	properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
@Import({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SwaggerConfig.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
class InspectionQuestionOpenApiContractTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private InspectionQuestionQueryFlow inspectionQuestionQueryFlow;

	@MockitoBean
	private InspectionQuestionLogic inspectionQuestionLogic;

	@MockitoBean
	private InspectionQuestionCategoryLogic inspectionQuestionCategoryLogic;

	@Test
	void generatedOpenApi_shouldDocumentOrderEndpointsAsNoContent() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode paths = objectMapper.readTree(json).path("paths");

		assertThat(responseCodes(paths, "/inspection-questions/order", "put")).containsExactly("204");
		assertThat(responseCodes(paths, "/inspection-question-categories/order", "put")).containsExactly("204");
	}

	private List<String> responseCodes(JsonNode paths, String path, String method) {
		List<String> codes = new ArrayList<>();
		paths.path(path).path(method).path("responses").fieldNames().forEachRemaining(codes::add);
		return codes;
	}
}
