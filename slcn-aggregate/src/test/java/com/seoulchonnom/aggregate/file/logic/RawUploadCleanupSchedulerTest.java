package com.seoulchonnom.aggregate.file.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import com.seoulchonnom.aggregate.config.SchedulingConfiguration;

class RawUploadCleanupSchedulerTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(SchedulingConfiguration.class)
		.withBean(RawUploadLogic.class, () -> mock(RawUploadLogic.class))
		.withBean(RawUploadCleanupScheduler.class);

	@Test
	void cleanupStaleUploads_shouldUseCutoffTwentyFourHoursAgo() {
		RawUploadLogic rawUploadLogic = mock(RawUploadLogic.class);
		RawUploadCleanupScheduler scheduler = new RawUploadCleanupScheduler(rawUploadLogic);
		long before = System.currentTimeMillis();

		scheduler.cleanupStaleUploads();

		long after = System.currentTimeMillis();
		ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
		verify(rawUploadLogic).cleanupPendingRegisteredBefore(cutoff.capture());
		long day = RawUploadCleanupScheduler.STALE_AFTER.toMillis();
		assertThat(cutoff.getValue()).isBetween(before - day, after - day);
	}

	/**
	 * @EnableScheduling이 빠지면 @Scheduled가 오류 없이 무시된다. 실제로 cron 작업이 등록되는지 확인한다.
	 */
	@Test
	void scheduling_shouldRegisterCronTaskWhenCronIsConfigured() {
		runner.withPropertyValues("slcn.storage.raw.cleanup-cron=0 30 4 * * *").run(context -> {
			ScheduledTaskHolder holder = context.getBean(ScheduledTaskHolder.class);

			assertThat(holder.getScheduledTasks())
				.extracting(scheduledTask -> scheduledTask.getTask())
				.filteredOn(CronTask.class::isInstance)
				.extracting(task -> ((CronTask)task).getExpression())
				.containsExactly("0 30 4 * * *");
		});
	}

	@Test
	void scheduling_shouldStayOffWithoutCronProperty() {
		runner.run(context -> {
			ScheduledTaskHolder holder = context.getBean(ScheduledTaskHolder.class);

			assertThat(holder.getScheduledTasks()).isEmpty();
			verify(context.getBean(RawUploadLogic.class), never()).cleanupPendingRegisteredBefore(anyLong());
		});
	}
}
