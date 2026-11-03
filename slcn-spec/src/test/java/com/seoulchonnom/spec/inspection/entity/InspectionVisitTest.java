package com.seoulchonnom.spec.inspection.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;

class InspectionVisitTest {
	private static InspectionVisit visit() {
		return new InspectionVisit("INSPECTION_VISIT-0001", "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
	}

	@Test
	void changeStatus_shouldRecordCompletedAtOnFirstCompletion() {
		InspectionVisit visit = visit();
		assertThat(visit.getCompletedAt()).isNull();
		LocalDateTime before = LocalDateTime.now();

		visit.changeStatus(InspectionStatus.COMPLETED);

		assertThat(visit.getCompletedAt()).isNotNull().isAfterOrEqualTo(before.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
		assertThat(visit.getCompletedAt().getNano()).isZero();
	}

	@Test
	void changeStatus_shouldKeepCompletedAtWhenRevertedToDraft() {
		InspectionVisit visit = visit();
		visit.changeStatus(InspectionStatus.COMPLETED);
		LocalDateTime first = visit.getCompletedAt();

		visit.changeStatus(InspectionStatus.DRAFT);

		assertThat(visit.getStatus()).isEqualTo(InspectionStatus.DRAFT);
		assertThat(visit.getCompletedAt()).isEqualTo(first);
	}

	@Test
	void changeStatus_shouldNotChangeCompletedAtWhenCompletedAgain() {
		InspectionVisit visit = visit();
		LocalDateTime first = LocalDateTime.of(2026, 9, 18, 10, 0);
		visit.setCompletedAt(first);
		visit.changeStatus(InspectionStatus.DRAFT);

		visit.changeStatus(InspectionStatus.COMPLETED);

		assertThat(visit.getCompletedAt()).isEqualTo(first);
	}

	@Test
	void changeStatus_shouldLeaveCompletedAtNullWhenNeverCompleted() {
		InspectionVisit visit = visit();

		visit.changeStatus(InspectionStatus.DRAFT);

		assertThat(visit.getCompletedAt()).isNull();
	}

	@Test
	void update_shouldNotResetCompletedAt() {
		InspectionVisit visit = visit();
		visit.changeStatus(InspectionStatus.COMPLETED);
		LocalDateTime first = visit.getCompletedAt();

		visit.update(LocalDateTime.of(2026, 9, 20, 9, 0), "메모", null, null, null, null);

		assertThat(visit.getCompletedAt()).isEqualTo(first);
	}
}
