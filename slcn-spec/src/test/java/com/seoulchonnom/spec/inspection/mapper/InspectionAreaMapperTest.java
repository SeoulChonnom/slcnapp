package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;

class InspectionAreaMapperTest {
	private final InspectionAreaMapper mapper = new InspectionAreaMapper();
	private final InspectionArea area = new InspectionArea("INSPECTION_AREA-0001", "성수동", null);

	@Test
	void toInspectionAreaRdo_shouldFormatPlannedVisitLikeOtherVisitedAt() {
		InspectionVisit plan = new InspectionVisit("INSPECTION_VISIT-0007", area.getId(),
			LocalDateTime.of(2026, 10, 12, 14, 0));

		InspectionAreaRdo rdo = mapper.toInspectionAreaRdo(area, 0, null, null, 0, null, null, null, List.of(), 0,
			plan);

		assertThat(rdo.getPlannedVisit().getInspectionVisitId()).isEqualTo("INSPECTION_VISIT-0007");
		assertThat(rdo.getPlannedVisit().getVisitedAt()).isEqualTo("2026-10-12T14:00");
	}

	@Test
	void toInspectionAreaRdo_shouldLeavePlannedVisitNullWhenNone() {
		InspectionAreaRdo rdo = mapper.toInspectionAreaRdo(area, 0, null, null, 0, null, null, null, List.of(), 0,
			null);

		assertThat(rdo.getPlannedVisit()).isNull();
	}
}
