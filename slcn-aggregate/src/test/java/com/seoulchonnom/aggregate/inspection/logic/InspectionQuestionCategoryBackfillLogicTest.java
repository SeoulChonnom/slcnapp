package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyConflictException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionCategoryBackfillLogic.BackfillReport;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionCategoryStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

class InspectionQuestionCategoryBackfillLogicTest {
	private final InspectionQuestionStore inspectionQuestionStore = mock(InspectionQuestionStore.class);
	private final InspectionQuestionCategoryStore inspectionQuestionCategoryStore =
		mock(InspectionQuestionCategoryStore.class);
	private final ViewedPropertyStore viewedPropertyStore = mock(ViewedPropertyStore.class);
	private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
	private final InspectionQuestionCategoryBackfillLogic backfillLogic = new InspectionQuestionCategoryBackfillLogic(
		inspectionQuestionStore, inspectionQuestionCategoryStore, viewedPropertyStore, transactionManager);

	private static InspectionQuestion questionOf(String id, String categoryId) {
		InspectionQuestion question = new InspectionQuestion(id, QuestionAnswerType.TEXT, true, 1);
		question.setCategoryId(categoryId);
		return question;
	}

	private static InspectionQuestionCategory categoryOf(String id, String name, int sortOrder) {
		return new InspectionQuestionCategory(id, name, sortOrder);
	}

	private static PropertyAnswer answerOf(String questionId, String categoryId) {
		PropertyAnswer answer = PropertyAnswer.builder()
			.questionId(questionId)
			.answerType(QuestionAnswerType.TEXT)
			.categoryId(categoryId)
			.build();
		return answer;
	}

	private static ViewedProperty propertyOf(String id, PropertyAnswer... answers) {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동 1203호", 1);
		property.setId(id);
		property.setAnswers(new ArrayList<>(List.of(answers)));
		return property;
	}

	@Test
	void backfill_shouldAbortWithoutTouchingAnythingWhenAnyMasterQuestionHasNoCategory() {
		// 사전 점검(계획 §5-1): 마스터 질문 중 하나라도 categoryId가 null이면 아무것도 건드리지 않는다.
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(questionOf("Q1", null)));

		BackfillReport report = backfillLogic.backfill();

		assertThat(report).isEqualTo(new BackfillReport(0, 0, 0, 0));
		verifyNoInteractions(viewedPropertyStore);
		assertThat(transactionManager.events).isEmpty();
	}

	@Test
	void backfill_shouldFillOnlyNullSnapshotFieldsAndLeaveAlreadyFilledAnswersIntact() {
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(questionOf("Q1", "C1"), questionOf("Q2", "C2")));
		when(inspectionQuestionCategoryStore.findAll()).thenReturn(
			List.of(categoryOf("C1", "채광·환기", 1), categoryOf("C2", "구조", 2)));
		when(viewedPropertyStore.findAllIds()).thenReturn(List.of("P1"));
		ViewedProperty property = propertyOf("P1", answerOf("Q1", null), answerOf("Q2", "ALREADY-SET"));
		when(viewedPropertyStore.findById("P1")).thenReturn(property);
		when(viewedPropertyStore.save(any())).thenReturn(property);

		BackfillReport report = backfillLogic.backfill();

		assertThat(report).isEqualTo(new BackfillReport(1, 0, 0, 0));
		PropertyAnswer filled = property.findAnswer("Q1").orElseThrow();
		assertThat(filled.getCategoryId()).isEqualTo("C1");
		assertThat(filled.getCategoryName()).isEqualTo("채광·환기");
		assertThat(filled.getCategorySortOrder()).isEqualTo(1);
		// 이미 채워져 있던 답변은 마스터 값으로 덮어쓰지 않는다.
		PropertyAnswer untouched = property.findAnswer("Q2").orElseThrow();
		assertThat(untouched.getCategoryId()).isEqualTo("ALREADY-SET");
		verify(viewedPropertyStore).save(property);
	}

	@Test
	void backfill_shouldNotSaveAndReportZeroUpdatedWhenRerunFindsNothingToFill() {
		// 재실행 시나리오: 모든 답변에 이미 categoryId가 있다.
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(questionOf("Q1", "C1")));
		when(inspectionQuestionCategoryStore.findAll()).thenReturn(List.of(categoryOf("C1", "채광·환기", 1)));
		when(viewedPropertyStore.findAllIds()).thenReturn(List.of("P1"));
		ViewedProperty property = propertyOf("P1", answerOf("Q1", "C1"));
		when(viewedPropertyStore.findById("P1")).thenReturn(property);

		BackfillReport report = backfillLogic.backfill();

		assertThat(report).isEqualTo(new BackfillReport(0, 1, 0, 0));
		verify(viewedPropertyStore, never()).save(any());
	}

	@Test
	void backfill_shouldCountOnePropertyAsFailedWhileOthersStillUpdate() {
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(questionOf("Q1", "C1")));
		when(inspectionQuestionCategoryStore.findAll()).thenReturn(List.of(categoryOf("C1", "채광·환기", 1)));
		when(viewedPropertyStore.findAllIds()).thenReturn(List.of("P1", "P2"));

		ViewedProperty failing = propertyOf("P1", answerOf("Q1", null));
		when(viewedPropertyStore.findById("P1")).thenReturn(failing);
		when(viewedPropertyStore.save(failing)).thenThrow(new ViewedPropertyConflictException());

		ViewedProperty succeeding = propertyOf("P2", answerOf("Q1", null));
		when(viewedPropertyStore.findById("P2")).thenReturn(succeeding);
		when(viewedPropertyStore.save(succeeding)).thenReturn(succeeding);

		BackfillReport report = backfillLogic.backfill();

		assertThat(report).isEqualTo(new BackfillReport(1, 0, 0, 1));
		// 매물마다 별도 트랜잭션으로 처리됐는지 - 실패한 매물은 rollback, 성공한 매물은 commit.
		assertThat(transactionManager.events).containsExactly("begin", "rollback", "begin", "commit");
	}

	@Test
	void backfill_shouldCountUnresolvedWhenMasterQuestionIsMissing() {
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(questionOf("Q1", "C1")));
		when(inspectionQuestionCategoryStore.findAll()).thenReturn(List.of(categoryOf("C1", "채광·환기", 1)));
		when(viewedPropertyStore.findAllIds()).thenReturn(List.of("P1"));
		// 이 답변의 questionId("GONE")는 현재 마스터 질문 목록에 없다.
		ViewedProperty property = propertyOf("P1", answerOf("GONE", null));
		when(viewedPropertyStore.findById("P1")).thenReturn(property);

		BackfillReport report = backfillLogic.backfill();

		assertThat(report).isEqualTo(new BackfillReport(0, 0, 1, 0));
		verify(viewedPropertyStore, never()).save(any());
	}

	@Test
	void backfill_shouldCountUnresolvedWhenMasterQuestionsCategoryIsMissingFromCategoryStore() {
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(questionOf("Q1", "C1")));
		// 질문은 C1을 가리키지만 분류 저장소에는 C1이 없다(방어적 케이스).
		when(inspectionQuestionCategoryStore.findAll()).thenReturn(List.of());
		when(viewedPropertyStore.findAllIds()).thenReturn(List.of("P1"));
		ViewedProperty property = propertyOf("P1", answerOf("Q1", null));
		when(viewedPropertyStore.findById("P1")).thenReturn(property);

		BackfillReport report = backfillLogic.backfill();

		assertThat(report).isEqualTo(new BackfillReport(0, 0, 1, 0));
		verify(viewedPropertyStore, never()).save(any());
	}

	@Test
	void backfill_shouldNotSaveWhenPropertyHasNoAnswers() {
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(questionOf("Q1", "C1")));
		when(inspectionQuestionCategoryStore.findAll()).thenReturn(List.of(categoryOf("C1", "채광·환기", 1)));
		when(viewedPropertyStore.findAllIds()).thenReturn(List.of("P1"));
		ViewedProperty property = propertyOf("P1");
		when(viewedPropertyStore.findById("P1")).thenReturn(property);

		BackfillReport report = backfillLogic.backfill();

		assertThat(report).isEqualTo(new BackfillReport(0, 1, 0, 0));
		verify(viewedPropertyStore, never()).save(any());
	}

	/**
	 * 실제 DB 없이 TransactionTemplate(PROPAGATION_REQUIRES_NEW)이 매물마다 독립된
	 * 트랜잭션을 여닫는지 증명하기 위한 최소 PlatformTransactionManager. commit/rollback
	 * 호출 순서를 그대로 기록한다.
	 */
	private static class RecordingTransactionManager implements PlatformTransactionManager {
		private final List<String> events = new ArrayList<>();

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
			assertThat(definition.getPropagationBehavior())
				.isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
			events.add("begin");
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) throws TransactionException {
			events.add("commit");
		}

		@Override
		public void rollback(TransactionStatus status) throws TransactionException {
			events.add("rollback");
		}
	}
}
