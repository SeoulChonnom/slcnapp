package com.seoulchonnom.boot.common.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.seoulchonnom.aggregate.schedule.feed.flow.ScheduleFeedFlow;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.rest.schedule.feed.ScheduleFeedResource;
import com.seoulchonnom.rest.schedule.feed.ScheduleIcsRenderer;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

@WebMvcTest(
	controllers = ScheduleFeedResource.class,
	properties = {
		"server.forward-headers-strategy=framework",
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
	})
@AutoConfigureMockMvc
@Import(ServletWebServerFactoryAutoConfiguration.class)
class ScheduleFeedForwardedHeadersTest {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ScheduleFeedTokenLogic scheduleFeedTokenLogic;

	@MockitoBean
	private ScheduleFeedFlow scheduleFeedFlow;

	@MockitoBean
	private ScheduleIcsRenderer scheduleIcsRenderer;

	@BeforeEach
	void setUp() {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name("Google Calendar")
			.tokenHash("hash")
			.build();
		token.setId("550e8400-e29b-41d4-a716-446655440000");
		token.setRegisteredTime(123L);
		when(scheduleFeedTokenLogic.create("Google Calendar"))
			.thenReturn(new ScheduleFeedTokenLogic.CreatedFeedToken(token, "raw-token"));
	}

	@Test
	void createFeed_shouldUseForwardedHttpsHostAndPrefixUnderFrameworkStrategy() throws Exception {
		mockMvc.perform(post("/schedule/feeds")
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-Host", "calendar.example.com")
				.header("X-Forwarded-Prefix", "/edge/api")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Google Calendar\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.feedUrl")
				.value("https://calendar.example.com/edge/api/schedule/feeds/raw-token/calendar.ics"));
	}
}
