package com.seoulchonnom.rest.schedule.feed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.seoulchonnom.aggregate.schedule.feed.flow.ScheduleFeedFlow;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

class ScheduleFeedResourceJsonContractTest {
	private ScheduleFeedTokenLogic scheduleFeedTokenLogic;
	private ScheduleFeedFlow scheduleFeedFlow;
	private ScheduleIcsRenderer scheduleIcsRenderer;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		scheduleFeedTokenLogic = mock(ScheduleFeedTokenLogic.class);
		scheduleFeedFlow = mock(ScheduleFeedFlow.class);
		scheduleIcsRenderer = mock(ScheduleIcsRenderer.class);
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ScheduleFeedResource(
			scheduleFeedTokenLogic, scheduleFeedFlow, scheduleIcsRenderer))
			.setControllerAdvice(new CommonExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void createFeed_shouldExposeOneTimeFeedUrlButNeverExposeTokenHash() throws Exception {
		ScheduleFeedToken token = token("FEED-0001", "Google Calendar", 123L, "sha256-secret");
		when(scheduleFeedTokenLogic.create("Google Calendar"))
			.thenReturn(new ScheduleFeedTokenLogic.CreatedFeedToken(token, "raw-token"));

		mockMvc.perform(post("/schedule/feeds")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Google Calendar\"}"))
			.andExpect(status().isCreated())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.id").value("FEED-0001"))
			.andExpect(jsonPath("$.name").value("Google Calendar"))
			.andExpect(jsonPath("$.feedUrl").value("http://localhost/schedule/feeds/raw-token/calendar.ics"))
			.andExpect(jsonPath("$.registeredTime").value(123))
			.andExpect(jsonPath("$.tokenHash").doesNotExist())
			.andExpect(jsonPath("$.rawToken").doesNotExist());

		verify(scheduleFeedTokenLogic).create("Google Calendar");
	}

	@Test
	void listFeeds_shouldExposeOnlySafeMetadataWithoutFeedUrlOrTokenHash() throws Exception {
		when(scheduleFeedTokenLogic.getAll())
			.thenReturn(List.of(token("FEED-0001", "Google Calendar", 123L, "sha256-secret")));

		mockMvc.perform(get("/schedule/feeds"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("FEED-0001"))
			.andExpect(jsonPath("$[0].name").value("Google Calendar"))
			.andExpect(jsonPath("$[0].registeredTime").value(123))
			.andExpect(jsonPath("$[0].feedUrl").doesNotExist())
			.andExpect(jsonPath("$[0].tokenHash").doesNotExist())
			.andExpect(jsonPath("$[0].rawToken").doesNotExist());

		verify(scheduleFeedTokenLogic).getAll();
	}

	@Test
	void createFeed_withBlankName_shouldRejectBeforeCallingLogic() throws Exception {
		mockMvc.perform(post("/schedule/feeds")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" \"}"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(scheduleFeedTokenLogic);
	}

	private ScheduleFeedToken token(String id, String name, long registeredTime, String tokenHash) {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name(name)
			.tokenHash(tokenHash)
			.build();
		token.setId(id);
		token.setRegisteredTime(registeredTime);
		return token;
	}
}
