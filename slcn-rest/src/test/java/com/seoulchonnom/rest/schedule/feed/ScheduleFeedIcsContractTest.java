package com.seoulchonnom.rest.schedule.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Component;

import com.seoulchonnom.aggregate.schedule.feed.exception.ScheduleFeedNotFoundException;
import com.seoulchonnom.aggregate.schedule.feed.flow.ScheduleFeedFlow;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedContent;

class ScheduleFeedIcsContractTest {
	private static final String RAW_TOKEN = "raw-token";
	private static final String ETAG = "\"etag-123\"";
	private static final String BODY = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

	private ScheduleFeedFlow scheduleFeedFlow;
	private ScheduleIcsRenderer scheduleIcsRenderer;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		scheduleFeedFlow = mock(ScheduleFeedFlow.class);
		scheduleIcsRenderer = mock(ScheduleIcsRenderer.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new ScheduleFeedResource(
			mock(ScheduleFeedTokenLogic.class), scheduleFeedFlow, scheduleIcsRenderer))
			.setControllerAdvice(new CommonExceptionHandler())
			.build();
	}

	@Test
	void getCalendar_shouldReturnUtf8CalendarBodyWithPrivateNoCacheAndEtag() throws Exception {
		givenRenderedCalendar();

		mockMvc.perform(get("/schedule/feeds/{feedToken}/calendar.ics", RAW_TOKEN))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8")))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("private")))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-cache")))
			.andExpect(header().string(HttpHeaders.ETAG, ETAG))
			.andExpect(content().string(BODY));

		verify(scheduleFeedFlow).getFeedContent(RAW_TOKEN);
		verify(scheduleIcsRenderer).render(new ScheduleFeedContent(null, List.of()));
	}

	@Test
	void getCalendar_shouldReturnValidEmptyVCalendarWhenNoEventsExist() throws Exception {
		ScheduleIcsRenderer realRenderer = new ScheduleIcsRenderer();
		when(scheduleFeedFlow.getFeedContent(RAW_TOKEN)).thenReturn(new ScheduleFeedContent(null, List.of()));
		ScheduleIcsRenderer.RenderedCalendar rendered = realRenderer.render(new ScheduleFeedContent(null, List.of()));
		when(scheduleIcsRenderer.render(new ScheduleFeedContent(null, List.of()))).thenReturn(rendered);

		String body = mockMvc.perform(get("/schedule/feeds/{feedToken}/calendar.ics", RAW_TOKEN))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		net.fortuna.ical4j.model.Calendar calendar = new CalendarBuilder().build(new StringReader(body));
		assertThat(calendar.getComponents(Component.VEVENT)).isEmpty();
		assertThat(body).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
	}

	@ParameterizedTest
	@ValueSource(strings = {"\"etag-123\"", "W/\"etag-123\"", "\"other\", W/\"etag-123\"", "*"})
	void getCalendar_shouldReturnNotModifiedForMatchingIfNoneMatchForms(String ifNoneMatch) throws Exception {
		givenRenderedCalendar();

		mockMvc.perform(get("/schedule/feeds/{feedToken}/calendar.ics", RAW_TOKEN)
				.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch))
			.andExpect(status().isNotModified())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, private"))
			.andExpect(header().string(HttpHeaders.ETAG, ETAG))
			.andExpect(content().string(""));
	}

	@Test
	void getCalendar_shouldReturnBodyWhenIfNoneMatchDoesNotMatch() throws Exception {
		givenRenderedCalendar();

		mockMvc.perform(get("/schedule/feeds/{feedToken}/calendar.ics", RAW_TOKEN)
				.header(HttpHeaders.IF_NONE_MATCH, "\"different-etag\""))
			.andExpect(status().isOk())
			.andExpect(content().string(BODY));
	}

	@Test
	void getCalendar_shouldReturnUniformNotFoundAndSkipRendererForInvalidToken() throws Exception {
		String invalidToken = "deleted-token";
		when(scheduleFeedFlow.getFeedContent(invalidToken)).thenThrow(new ScheduleFeedNotFoundException());

		mockMvc.perform(get("/schedule/feeds/{feedToken}/calendar.ics", invalidToken)
				.header(HttpHeaders.IF_NONE_MATCH, ETAG))
			.andExpect(status().isNotFound())
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(invalidToken))))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("해당 일정 피드가 없습니다.")));

		verifyNoInteractions(scheduleIcsRenderer);
	}

	private void givenRenderedCalendar() {
		when(scheduleFeedFlow.getFeedContent(RAW_TOKEN)).thenReturn(new ScheduleFeedContent(null, List.of()));
		when(scheduleIcsRenderer.render(new ScheduleFeedContent(null, List.of()))).thenReturn(
			new ScheduleIcsRenderer.RenderedCalendar(BODY, ETAG));
	}
}
