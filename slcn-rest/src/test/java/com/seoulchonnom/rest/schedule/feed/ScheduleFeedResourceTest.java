package com.seoulchonnom.rest.schedule.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

class ScheduleFeedResourceTest {
	private ScheduleFeedTokenLogic scheduleFeedTokenLogic;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		scheduleFeedTokenLogic = mock(ScheduleFeedTokenLogic.class);
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ScheduleFeedResource(scheduleFeedTokenLogic))
			.setControllerAdvice(new CommonExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void createFeed_shouldReturnCreatedStatusAndContextPathAwareSubscriptionUrl() throws Exception {
		ScheduleFeedToken token = token("FEED-0001", "Google Calendar", 123L, "hash");
		when(scheduleFeedTokenLogic.create("Google Calendar"))
			.thenReturn(new ScheduleFeedTokenLogic.CreatedFeedToken(token, "raw-token"));

		mockMvc.perform(post("/api/schedule/feeds")
				.contextPath("/api")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Google Calendar\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.feedUrl").value("http://localhost/api/schedule/feeds/raw-token/calendar.ics"));

		ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
		verify(scheduleFeedTokenLogic).create(nameCaptor.capture());
		assertThat(nameCaptor.getValue()).isEqualTo("Google Calendar");
	}

	@Test
	void getFeeds_shouldReturnOkAndDelegateToLogic() throws Exception {
		when(scheduleFeedTokenLogic.getAll()).thenReturn(List.of(token("FEED-0001", "Google Calendar", 123L, "hash")));

		mockMvc.perform(get("/schedule/feeds"))
			.andExpect(status().isOk());

		verify(scheduleFeedTokenLogic).getAll();
	}

	@Test
	void deleteFeed_shouldReturnNoContentAndDelegateToLogic() throws Exception {
		mockMvc.perform(delete("/schedule/feeds/FEED-0001"))
			.andExpect(status().isNoContent());

		verify(scheduleFeedTokenLogic).delete("FEED-0001");
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
