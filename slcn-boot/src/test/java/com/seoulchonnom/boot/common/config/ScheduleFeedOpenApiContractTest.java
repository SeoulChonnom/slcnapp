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

import com.seoulchonnom.aggregate.schedule.feed.flow.ScheduleFeedFlow;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.rest.schedule.feed.ScheduleFeedResource;
import com.seoulchonnom.rest.schedule.feed.ScheduleIcsRenderer;

@WebMvcTest(
	controllers = ScheduleFeedResource.class,
	properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
@Import({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SwaggerConfig.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
class ScheduleFeedOpenApiContractTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ScheduleFeedTokenLogic scheduleFeedTokenLogic;

	@MockitoBean
	private ScheduleFeedFlow scheduleFeedFlow;

	@MockitoBean
	private ScheduleIcsRenderer scheduleIcsRenderer;

	@Test
	void generatedOpenApi_shouldSecureManagementOperationsButLeaveIcsFeedPublic() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode paths = objectMapper.readTree(json).path("paths");

		assertThat(security(paths, "/schedule/feeds", "post")).containsExactly("X-AUTH-TOKEN");
		assertThat(security(paths, "/schedule/feeds", "get")).containsExactly("X-AUTH-TOKEN");
		assertThat(security(paths, "/schedule/feeds/{feedId}", "delete")).containsExactly("X-AUTH-TOKEN");
		assertThat(security(paths, "/schedule/feeds/{feedToken}/calendar.ics", "get")).isEmpty();
	}

	private List<String> security(JsonNode paths, String path, String method) {
		JsonNode security = paths.path(path).path(method).path("security");
		if (!security.isArray()) {
			return List.of();
		}

		List<String> names = new ArrayList<>();
		security.forEach(requirement -> requirement.fieldNames().forEachRemaining(names::add));
		return names;
	}
}
