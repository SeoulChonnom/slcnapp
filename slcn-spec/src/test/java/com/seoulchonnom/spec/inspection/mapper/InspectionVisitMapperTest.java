package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;

class InspectionVisitMapperTest {
	private final InspectionVisitMapper mapper = new InspectionVisitMapper();
	private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 10, 8, 14, 30);

	private static InspectionVisit visit(LocalDateTime completedAt) {
		InspectionVisit visit = new InspectionVisit("v1", "a1", LocalDateTime.of(2026, 9, 17, 14, 0));
		visit.setCompletedAt(completedAt);
		visit.setStatus(InspectionStatus.DRAFT);
		return visit;
	}

	private static InspectionArea area() {
		return new InspectionArea("a1", "성수동", null);
	}

	@Test
	void toInspectionVisitSummaryRdo_shouldCarryCompletedAtInVisitedAtFormat() {
		assertThat(mapper.toInspectionVisitSummaryRdo(visit(COMPLETED_AT), null, null, null, null).getCompletedAt())
			.isEqualTo("2026-10-08T14:30");
		assertThat(mapper.toInspectionVisitSummaryRdo(visit(null), null, null, null, null).getCompletedAt())
			.isNull();
	}

	@Test
	void toInspectionVisitRdo_shouldCarryCompletedAtInVisitedAtFormat() {
		assertThat(mapper.toInspectionVisitRdo(visit(COMPLETED_AT), area(), 0, null, null, null, null)
			.getCompletedAt()).isEqualTo("2026-10-08T14:30");
		assertThat(mapper.toInspectionVisitRdo(visit(null), area(), 0, null, null, null, null).getCompletedAt())
			.isNull();
	}

	@Test
	void toInspectionVisitDetailRdo_shouldCarryCompletedAtInVisitedAtFormat() {
		assertThat(mapper.toInspectionVisitDetailRdo(visit(COMPLETED_AT), area(), null, null, null, null)
			.getCompletedAt()).isEqualTo("2026-10-08T14:30");
		assertThat(mapper.toInspectionVisitDetailRdo(visit(null), area(), null, null, null, null).getCompletedAt())
			.isNull();
	}
}
