package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.logic.ViewedPropertyLogic;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.facade.sdo.IncompleteSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.UnansweredQuestionRdo;
import com.seoulchonnom.spec.inspection.mapper.PropertyAnswerMapper;

class InspectionSummarySupportTest {
	private final InspectionSummarySupport inspectionSummarySupport = new InspectionSummarySupport();
	private final ViewedPropertyLogic viewedPropertyLogic = new ViewedPropertyLogic(
		mock(ViewedPropertyStore.class), new PropertyAnswerMapper());

	private static ViewedPropertySummaryPdo pdo(InspectionStatus status, int unanswered) {
		ViewedPropertySummaryPdo pdo = mock(ViewedPropertySummaryPdo.class);
		when(pdo.getStatus()).thenReturn(status);
		when(pdo.getUnansweredRequiredCount()).thenReturn(unanswered);
		return pdo;
	}

	private static InspectionVisit visit(InspectionStatus status) {
		InspectionVisit visit = new InspectionVisit("INSPECTION_VISIT-0001", "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
		visit.changeStatus(status);
		return visit;
	}

	private static InspectionQuestion question(String id, boolean required) {
		InspectionQuestion question = new InspectionQuestion(id, QuestionAnswerType.TEXT, required, 2);
		question.addVersion("질문 " + id, null, null, null);
		return question;
	}

	@Test
	void ofProperty_shouldListEveryUnmetCompletionCondition() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", true), question("q2", false)));

		IncompleteSummaryRdo summary = inspectionSummarySupport.ofProperty(property);

		assertThat(summary.getUnansweredRequiredCount()).isEqualTo(1);
		assertThat(summary.getUnansweredRequiredQuestions())
			.extracting(UnansweredQuestionRdo::getQuestionId).containsExactly("q1");
		assertThat(summary.getUnansweredRequiredQuestions().get(0).getQuestion()).isEqualTo("질문 q1");
		// interestLevel만 비어 있다. complexName/name은 채워져 있다
		assertThat(summary.getMissingFields()).containsExactly("interestLevel");
	}

	@Test
	void ofProperty_shouldMatchTheLogicCompletionCheck() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);
		property.setInterestLevel(4);
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", false)));

		IncompleteSummaryRdo summary = inspectionSummarySupport.ofProperty(property);

		// 요약이 비었으면 완료 검증도 통과해야 한다. 어긋나면 "0개 남았는데 완료가 안 되는" 상태가 생긴다
		assertThat(summary.getMissingFields()).isEmpty();
		assertThat(summary.getUnansweredRequiredCount()).isZero();
		assertThatCode(() -> viewedPropertyLogic.validateCompletable(property)).doesNotThrowAnyException();
	}

	@Test
	void ofVisit_shouldCarryVisitLevelCondition() {
		IncompleteSummaryRdo summary = inspectionSummarySupport.ofVisit(visit(InspectionStatus.DRAFT),
			List.of(pdo(InspectionStatus.DRAFT, 2), pdo(InspectionStatus.COMPLETED, 0)));

		assertThat(summary.getDraftPropertyCount()).isEqualTo(1);
		assertThat(summary.getUnansweredRequiredCount()).isEqualTo(2);
		// 이 필드가 없으면 "재방문 의사 미입력" 때문에 완료가 막힌 것을 화면이 설명할 수 없다
		assertThat(summary.getVisitMissingFields()).containsExactly("revisitIntent");
	}

	@Test
	void ofArea_shouldCountDraftVisits() {
		IncompleteSummaryRdo summary = inspectionSummarySupport.ofArea(
			List.of(visit(InspectionStatus.DRAFT), visit(InspectionStatus.COMPLETED)),
			List.of(pdo(InspectionStatus.DRAFT, 3)));

		assertThat(summary.getDraftVisitCount()).isEqualTo(1);
		assertThat(summary.getDraftPropertyCount()).isEqualTo(1);
		assertThat(summary.getUnansweredRequiredCount()).isEqualTo(3);
	}

	@Test
	void ofArea_shouldBeEmptyForAreaWithoutVisit() {
		IncompleteSummaryRdo summary = inspectionSummarySupport.ofArea(List.of(), List.of());

		assertThat(summary.getDraftVisitCount()).isZero();
		assertThat(summary.getDraftPropertyCount()).isZero();
		assertThat(summary.getUnansweredRequiredCount()).isZero();
	}
}
