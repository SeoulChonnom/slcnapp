package com.seoulchonnom.aggregate.house.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import com.seoulchonnom.aggregate.config.SchedulingConfiguration;
import com.seoulchonnom.aggregate.flow.house.HouseNightlyFlow;

class HouseNightlySchedulerTest {
	// 서울 기준 2026-11-04 03:00 = UTC 2026-11-03 18:00. 서울 날짜로 넘겨야 한다.
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T18:00:00Z"), ZoneId.of("Asia/Seoul"));
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(SchedulingConfiguration.class)
		.withBean(HouseNightlyFlow.class, () -> mock(HouseNightlyFlow.class))
		.withBean(Clock.class, () -> clock)
		.withBean(HouseNightlyScheduler.class);

	@Test
	void runNightly_shouldPassSeoulTodayToFlow() {
		HouseNightlyFlow flow = mock(HouseNightlyFlow.class);

		new HouseNightlyScheduler(flow, clock).runNightly();

		verify(flow).run(LocalDate.of(2026, 11, 4));
	}

	@Test
	void runNightly_shouldNotThrowWhenFlowFails() {
		HouseNightlyFlow flow = mock(HouseNightlyFlow.class);
		doThrow(new IllegalStateException("https://x?serviceKey=secret")).when(flow).run(any());

		assertThatCode(() -> new HouseNightlyScheduler(flow, clock).runNightly()).doesNotThrowAnyException();
	}

	@Test
	void scheduling_shouldRegisterCronTaskWhenCronIsConfigured() {
		runner.withPropertyValues("slcn.house.nightly-cron=0 0 3 * * *").run(context -> {
			ScheduledTaskHolder holder = context.getBean(ScheduledTaskHolder.class);

			assertThat(holder.getScheduledTasks())
				.extracting(scheduledTask -> scheduledTask.getTask())
				.filteredOn(CronTask.class::isInstance)
				.extracting(task -> ((CronTask)task).getExpression())
				.containsExactly("0 0 3 * * *");
		});
	}

	@Test
	void scheduling_shouldStayOffWithoutCronProperty() {
		runner.run(context -> {
			ScheduledTaskHolder holder = context.getBean(ScheduledTaskHolder.class);

			assertThat(holder.getScheduledTasks()).isEmpty();
			verify(context.getBean(HouseNightlyFlow.class), never()).run(any());
		});
	}
}
