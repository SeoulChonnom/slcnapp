package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionVisitException;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;

class InspectionVisitLogicTest {
	private final InspectionVisitStore inspectionVisitStore = mock(InspectionVisitStore.class);
	private final InspectionVisitLogic inspectionVisitLogic = new InspectionVisitLogic(inspectionVisitStore);

	private static InspectionVisit visit() {
		return new InspectionVisit("INSPECTION_VISIT-0001", "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
	}

	private static InspectionVisitUdo udo(String visitedAt) {
		InspectionVisitUdo udo = new InspectionVisitUdo();
		udo.setVisitedAt(visitedAt);
		return udo;
	}

	@Test
	void parseVisitedAt_shouldRejectBlank() {
		assertThatThrownBy(() -> inspectionVisitLogic.parseVisitedAt("  "))
			.isInstanceOf(InvalidInspectionVisitException.class);
	}

	@Test
	void parseVisitedAt_shouldRejectMalformedValue() {
		assertThatThrownBy(() -> inspectionVisitLogic.parseVisitedAt("2026-09-17"))
			.isInstanceOf(InvalidInspectionVisitException.class);
	}

	@Test
	void parseVisitedAt_shouldAcceptIsoLocalDateTime() {
		assertThat(inspectionVisitLogic.parseVisitedAt("2026-09-17T14:00:00"))
			.isEqualTo(LocalDateTime.of(2026, 9, 17, 14, 0));
	}

	@Test
	void applyUpdate_shouldClearOmittedScalarFields() {
		InspectionVisit visit = visit();
		visit.setMemo("이전 메모");
		visit.setRevisitIntent(RevisitIntent.YES);
		visit.setPros("이전 장점");

		// PUT은 전체 교체다. 생략된 스칼라는 null로 덮어쓴다
		inspectionVisitLogic.applyUpdate(visit, udo("2026-09-17T14:00:00"));

		assertThat(visit.getMemo()).isNull();
		assertThat(visit.getRevisitIntent()).isNull();
		assertThat(visit.getPros()).isNull();
	}

	@Test
	void applyUpdate_shouldTrimTextFields() {
		InspectionVisit visit = visit();
		InspectionVisitUdo udo = udo("2026-09-17T14:00:00");
		udo.setMemo("  서울숲 접근성 좋음  ");
		udo.setOneLineReview("   ");

		inspectionVisitLogic.applyUpdate(visit, udo);

		assertThat(visit.getMemo()).isEqualTo("서울숲 접근성 좋음");
		assertThat(visit.getOneLineReview()).isNull();
	}

	@Test
	void applyUpdate_shouldRejectTooLongOneLineReview() {
		InspectionVisitUdo udo = udo("2026-09-17T14:00:00");
		udo.setOneLineReview("가".repeat(301));

		assertThatThrownBy(() -> inspectionVisitLogic.applyUpdate(visit(), udo))
			.isInstanceOf(InvalidInspectionVisitException.class);
	}

	@Test
	void findMissingFieldsForCompletion_shouldReportRevisitIntent() {
		InspectionVisit visit = visit();

		assertThat(inspectionVisitLogic.findMissingFieldsForCompletion(visit)).containsExactly("revisitIntent");

		visit.setRevisitIntent(RevisitIntent.MAYBE);
		assertThat(inspectionVisitLogic.findMissingFieldsForCompletion(visit)).isEmpty();
	}
}
