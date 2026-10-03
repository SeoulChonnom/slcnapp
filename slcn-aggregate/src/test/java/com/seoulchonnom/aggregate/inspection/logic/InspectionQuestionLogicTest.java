package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDisabledException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryRequiredException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionReactivationBlockedException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionQuestionException;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionCategoryStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryMoveUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionPolicyUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.QuestionChoiceSdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionQuestionMapper;

class InspectionQuestionLogicTest {
	private static final String CATEGORY_ID = "INSPECTION_QUESTION_CATEGORY-0001";

	private final InspectionQuestionStore inspectionQuestionStore = mock(InspectionQuestionStore.class);
	private final InspectionQuestionCategoryStore inspectionQuestionCategoryStore =
		mock(InspectionQuestionCategoryStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final InspectionQuestionLogic inspectionQuestionLogic = new InspectionQuestionLogic(
		inspectionQuestionStore, inspectionQuestionCategoryStore, new InspectionQuestionMapper(), idGenerator);

	private static InspectionQuestionCdo cdo(QuestionAnswerType answerType, String content) {
		InspectionQuestionCdo cdo = new InspectionQuestionCdo();
		cdo.setAnswerType(answerType);
		cdo.setContent(content);
		cdo.setRequired(true);
		cdo.setSortOrder(1);
		cdo.setCategoryId(CATEGORY_ID);
		return cdo;
	}

	private static InspectionQuestionContentUdo contentUdo(String content) {
		InspectionQuestionContentUdo udo = new InspectionQuestionContentUdo();
		udo.setContent(content);
		return udo;
	}

	private static InspectionQuestionCategory enabledCategory(String categoryId) {
		return new InspectionQuestionCategory(categoryId, "채광·환기", 1);
	}

	private static InspectionQuestionCategory disabledCategory(String categoryId) {
		InspectionQuestionCategory category = enabledCategory(categoryId);
		category.changeEnabled(false);
		return category;
	}

	private void givenEnabledCategory() {
		when(inspectionQuestionCategoryStore.findById(CATEGORY_ID)).thenReturn(enabledCategory(CATEGORY_ID));
	}

	private InspectionQuestion savedQuestion() {
		ArgumentCaptor<InspectionQuestion> captor = ArgumentCaptor.forClass(InspectionQuestion.class);
		verify(inspectionQuestionStore).save(captor.capture());
		return captor.getValue();
	}

	private void echoSave() {
		when(inspectionQuestionStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registerInspectionQuestion_shouldCreateFirstVersion() {
		when(idGenerator.nextDomainId("INSPECTION_QUESTION")).thenReturn("INSPECTION_QUESTION-0001");
		givenEnabledCategory();
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.registerInspectionQuestion(
			cdo(QuestionAnswerType.LONG_TEXT, "채광은 어떤가?"));

		assertThat(rdo.getQuestionId()).isEqualTo("INSPECTION_QUESTION-0001");
		assertThat(rdo.getCurrentVersionNo()).isEqualTo(1);
		assertThat(rdo.getContent()).isEqualTo("채광은 어떤가?");
		assertThat(rdo.getCategoryId()).isEqualTo(CATEGORY_ID);
		assertThat(rdo.getCategoryName()).isEqualTo("채광·환기");
		assertThat(savedQuestion().isEnabled()).isTrue();
	}

	@Test
	void registerInspectionQuestion_shouldRejectMissingAnswerType() {
		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(cdo(null, "채광은?")))
			.isInstanceOf(InvalidInspectionQuestionException.class);
		verifyNoInteractions(idGenerator);
	}

	@Test
	void registerInspectionQuestion_shouldRejectBlankContent() {
		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(
			cdo(QuestionAnswerType.TEXT, "   ")))
			.isInstanceOf(InvalidInspectionQuestionException.class);
	}

	@Test
	void registerInspectionQuestion_shouldRequireChoicesForSelectType() {
		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(
			cdo(QuestionAnswerType.SINGLE_SELECT, "방향은?")))
			.isInstanceOf(InvalidInspectionQuestionException.class);
	}

	@Test
	void registerInspectionQuestion_shouldRejectChoicesOnNonSelectType() {
		InspectionQuestionCdo cdo = cdo(QuestionAnswerType.TEXT, "메모");
		cdo.setChoices(List.of(new QuestionChoiceSdo("A", "가", 1)));

		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(cdo))
			.isInstanceOf(InvalidInspectionQuestionException.class);
	}

	@Test
	void registerInspectionQuestion_shouldRejectDuplicatedChoiceCode() {
		InspectionQuestionCdo cdo = cdo(QuestionAnswerType.MULTI_SELECT, "방향은?");
		cdo.setChoices(List.of(new QuestionChoiceSdo("S", "남향", 1), new QuestionChoiceSdo("S", "남서향", 2)));

		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(cdo))
			.isInstanceOf(InvalidInspectionQuestionException.class);
	}

	@Test
	void registerInspectionQuestion_shouldRejectUnitOnNonNumberType() {
		InspectionQuestionCdo cdo = cdo(QuestionAnswerType.TEXT, "전용면적 메모");
		cdo.setUnit("m2");

		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(cdo))
			.isInstanceOf(InvalidInspectionQuestionException.class);
	}

	/**
	 * 계획 §0-1: 등록 시 분류는 필수다. DB 컬럼은 nullable이라 이 필수 규칙은 여기서 지킨다.
	 */
	@Test
	void registerInspectionQuestion_shouldRejectBlankCategoryId() {
		InspectionQuestionCdo cdo = cdo(QuestionAnswerType.TEXT, "메모");
		cdo.setCategoryId("   ");

		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(cdo))
			.isInstanceOf(InspectionQuestionCategoryRequiredException.class);
		verifyNoInteractions(idGenerator);
	}

	@Test
	void registerInspectionQuestion_shouldRejectUnknownCategory() {
		when(inspectionQuestionCategoryStore.findById(CATEGORY_ID))
			.thenThrow(new InspectionQuestionCategoryNotFoundException());

		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(
			cdo(QuestionAnswerType.TEXT, "메모")))
			.isInstanceOf(InspectionQuestionCategoryNotFoundException.class);
		verify(inspectionQuestionStore, never()).save(any());
	}

	@Test
	void registerInspectionQuestion_shouldRejectDisabledCategory() {
		when(inspectionQuestionCategoryStore.findById(CATEGORY_ID)).thenReturn(disabledCategory(CATEGORY_ID));

		assertThatThrownBy(() -> inspectionQuestionLogic.registerInspectionQuestion(
			cdo(QuestionAnswerType.TEXT, "메모")))
			.isInstanceOf(InspectionQuestionCategoryDisabledException.class);
		verify(inspectionQuestionStore, never()).save(any());
	}

	/**
	 * sortOrder가 0 이하면 분류 안 맨 뒤(max+1)로 채번한다(계획 §0-4).
	 */
	@Test
	void registerInspectionQuestion_shouldAutoAssignSortOrderWithinCategoryWhenNotPositive() {
		when(idGenerator.nextDomainId(anyString())).thenReturn("INSPECTION_QUESTION-0001");
		givenEnabledCategory();
		when(inspectionQuestionStore.findMaxSortOrderInCategory(CATEGORY_ID)).thenReturn(3);
		echoSave();

		InspectionQuestionCdo cdo = cdo(QuestionAnswerType.TEXT, "메모");
		cdo.setSortOrder(0);

		InspectionQuestionRdo rdo = inspectionQuestionLogic.registerInspectionQuestion(cdo);

		assertThat(rdo.getSortOrder()).isEqualTo(4);
		verify(inspectionQuestionStore).findMaxSortOrderInCategory(CATEGORY_ID);
	}

	@Test
	void registerInspectionQuestion_shouldKeepGivenPositiveSortOrder() {
		when(idGenerator.nextDomainId(anyString())).thenReturn("INSPECTION_QUESTION-0001");
		givenEnabledCategory();
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.registerInspectionQuestion(
			cdo(QuestionAnswerType.TEXT, "메모"));

		assertThat(rdo.getSortOrder()).isEqualTo(1);
		verify(inspectionQuestionStore, never()).findMaxSortOrderInCategory(anyString());
	}

	@Test
	void modifyInspectionQuestionContent_shouldAppendVersionAndKeepTheOldOne() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.LONG_TEXT, true, 1);
		question.addVersion("거실 채광은 어떤가?", "v1 도움말", null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		givenEnabledCategory();
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.modifyInspectionQuestionContent(
			"INSPECTION_QUESTION-0001", contentUdo("거실 및 방의 채광은 어떤가?"));

		assertThat(rdo.getCurrentVersionNo()).isEqualTo(2);
		assertThat(rdo.getContent()).isEqualTo("거실 및 방의 채광은 어떤가?");
		assertThat(question.findVersion(1).orElseThrow().getContent()).isEqualTo("거실 채광은 어떤가?");
		assertThat(question.findVersion(1).orElseThrow().getDescription()).isEqualTo("v1 도움말");
	}

	@Test
	void modifyInspectionQuestionContent_shouldValidateAgainstStoredAnswerType() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.SINGLE_SELECT, true, 1);
		question.addVersion("방향은?", null, List.of(new QuestionChoice("S", "남향", 1)), null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);

		// 선택형인데 선택지를 비워 보내면 막힌다. 요청에 answerType이 없어도 저장된 타입으로 판정한다
		assertThatThrownBy(() -> inspectionQuestionLogic.modifyInspectionQuestionContent(
			"INSPECTION_QUESTION-0001", contentUdo("주된 방향은?")))
			.isInstanceOf(InvalidInspectionQuestionException.class);
		assertThat(question.getCurrentVersionNo()).isEqualTo(1);
	}

	@Test
	void modifyInspectionQuestionPolicy_shouldKeepVersion() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		question.addVersion("메모", null, null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		givenEnabledCategory();
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.modifyInspectionQuestionPolicy(
			"INSPECTION_QUESTION-0001", new InspectionQuestionPolicyUdo(false, 9));

		assertThat(rdo.isRequired()).isFalse();
		assertThat(rdo.getSortOrder()).isEqualTo(9);
		assertThat(rdo.getCurrentVersionNo()).isEqualTo(1);
		assertThat(question.getVersions()).hasSize(1);
	}

	@Test
	void changeInspectionQuestionStatus_shouldKeepVersion() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		question.addVersion("메모", null, null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		givenEnabledCategory();
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.changeInspectionQuestionStatus(
			"INSPECTION_QUESTION-0001", new InspectionQuestionStatusUdo(false));

		assertThat(rdo.isEnabled()).isFalse();
		assertThat(rdo.getCurrentVersionNo()).isEqualTo(1);
	}

	/**
	 * 비활성 분류에 속한 질문은 다시 활성화할 수 없다(계획 §1) - 그렇지 않으면 분류 비활성화
	 * 조건(결정 3)을 우회할 수 있다.
	 */
	@Test
	void changeInspectionQuestionStatus_shouldRejectEnablingWhenCategoryDisabled() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		question.changeEnabled(false);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		when(inspectionQuestionCategoryStore.findById(CATEGORY_ID)).thenReturn(disabledCategory(CATEGORY_ID));

		assertThatThrownBy(() -> inspectionQuestionLogic.changeInspectionQuestionStatus(
			"INSPECTION_QUESTION-0001", new InspectionQuestionStatusUdo(true)))
			.isInstanceOf(InspectionQuestionReactivationBlockedException.class);
		assertThat(question.isEnabled()).isFalse();
		verify(inspectionQuestionStore, never()).save(any());
	}

	@Test
	void changeInspectionQuestionStatus_shouldAllowEnablingWhenCategoryEnabled() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		question.changeEnabled(false);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		givenEnabledCategory();
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.changeInspectionQuestionStatus(
			"INSPECTION_QUESTION-0001", new InspectionQuestionStatusUdo(true));

		assertThat(rdo.isEnabled()).isTrue();
	}

	@Test
	void getInspectionQuestionVersions_shouldReturnWholeHistoryWithCurrentFlag() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		question.addVersion("v1", null, null, null);
		question.addVersion("v2", null, null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);

		List<InspectionQuestionVersionRdo> versions = inspectionQuestionLogic.getInspectionQuestionVersions(
			"INSPECTION_QUESTION-0001");

		assertThat(versions).hasSize(2);
		assertThat(versions.get(0).isCurrent()).isFalse();
		assertThat(versions.get(1).isCurrent()).isTrue();
	}

	@Test
	void modifyInspectionQuestionOrder_shouldRejectUnknownQuestion() {
		when(inspectionQuestionStore.findAllByIds(anyCollection())).thenReturn(List.of());

		assertThatThrownBy(() -> inspectionQuestionLogic.modifyInspectionQuestionOrder(
			List.of(new InspectionQuestionOrderUdo("INSPECTION_QUESTION-9999", 1))))
			.isInstanceOf(InvalidInspectionQuestionException.class);
		verify(inspectionQuestionStore, never()).saveAll(any());
	}

	@Test
	void modifyInspectionQuestionOrder_shouldApplyRequestedOrderOnly() {
		InspectionQuestion first = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		InspectionQuestion second = new InspectionQuestion("INSPECTION_QUESTION-0002", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 2);
		when(inspectionQuestionStore.findAllByIds(anyCollection())).thenReturn(List.of(first, second));

		inspectionQuestionLogic.modifyInspectionQuestionOrder(List.of(
			new InspectionQuestionOrderUdo("INSPECTION_QUESTION-0001", 5),
			new InspectionQuestionOrderUdo("INSPECTION_QUESTION-0002", 3)));

		assertThat(first.getSortOrder()).isEqualTo(5);
		assertThat(second.getSortOrder()).isEqualTo(3);
		verify(inspectionQuestionStore).saveAll(List.of(first, second));
	}

	@Test
	void moveInspectionQuestionCategory_shouldRejectBlankCategoryId() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);

		assertThatThrownBy(() -> inspectionQuestionLogic.moveInspectionQuestionCategory(
			"INSPECTION_QUESTION-0001", new InspectionQuestionCategoryMoveUdo("   ")))
			.isInstanceOf(InspectionQuestionCategoryRequiredException.class);
		verify(inspectionQuestionStore, never()).save(any());
		verifyNoInteractions(inspectionQuestionCategoryStore);
	}

	@Test
	void moveInspectionQuestionCategory_shouldRejectUnknownCategory() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		when(inspectionQuestionCategoryStore.findById("CATEGORY-UNKNOWN"))
			.thenThrow(new InspectionQuestionCategoryNotFoundException());

		assertThatThrownBy(() -> inspectionQuestionLogic.moveInspectionQuestionCategory(
			"INSPECTION_QUESTION-0001", new InspectionQuestionCategoryMoveUdo("CATEGORY-UNKNOWN")))
			.isInstanceOf(InspectionQuestionCategoryNotFoundException.class);
		verify(inspectionQuestionStore, never()).save(any());
	}

	@Test
	void moveInspectionQuestionCategory_shouldRejectDisabledCategory() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		when(inspectionQuestionCategoryStore.findById(CATEGORY_ID)).thenReturn(disabledCategory(CATEGORY_ID));

		assertThatThrownBy(() -> inspectionQuestionLogic.moveInspectionQuestionCategory(
			"INSPECTION_QUESTION-0001", new InspectionQuestionCategoryMoveUdo(CATEGORY_ID)))
			.isInstanceOf(InspectionQuestionCategoryDisabledException.class);
		verify(inspectionQuestionStore, never()).save(any());
	}

	/**
	 * 이동하면 항상 대상 분류의 맨 뒤(max+1)로 배치되고, 버전/현재버전은 그대로다(계획 §1).
	 */
	@Test
	void moveInspectionQuestionCategory_shouldPlaceAtEndAndKeepVersion() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 1);
		question.setCategoryId("CATEGORY-OLD");
		question.addVersion("메모", null, null, null);
		question.addVersion("메모 v2", null, null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		when(inspectionQuestionCategoryStore.findById(CATEGORY_ID)).thenReturn(enabledCategory(CATEGORY_ID));
		when(inspectionQuestionStore.findMaxSortOrderInCategory(CATEGORY_ID)).thenReturn(4);
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.moveInspectionQuestionCategory(
			"INSPECTION_QUESTION-0001", new InspectionQuestionCategoryMoveUdo(CATEGORY_ID));

		assertThat(rdo.getCategoryId()).isEqualTo(CATEGORY_ID);
		assertThat(rdo.getSortOrder()).isEqualTo(5);
		assertThat(rdo.getCurrentVersionNo()).isEqualTo(2);
		assertThat(question.getVersions()).hasSize(2);
		verify(inspectionQuestionStore).save(question);
	}

	/**
	 * 이미 그 분류에 있으면 재배치하지 않는다 - 불필요한 저장으로 sortOrder가 흔들리지 않게 한다.
	 */
	@Test
	void moveInspectionQuestionCategory_shouldNoOpWhenAlreadyInTargetCategory() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", CATEGORY_ID,
			QuestionAnswerType.TEXT, true, 7);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		when(inspectionQuestionCategoryStore.findById(CATEGORY_ID)).thenReturn(enabledCategory(CATEGORY_ID));

		InspectionQuestionRdo rdo = inspectionQuestionLogic.moveInspectionQuestionCategory(
			"INSPECTION_QUESTION-0001", new InspectionQuestionCategoryMoveUdo(CATEGORY_ID));

		assertThat(rdo.getSortOrder()).isEqualTo(7);
		verify(inspectionQuestionStore, never()).save(any());
		verify(inspectionQuestionStore, never()).findMaxSortOrderInCategory(anyString());
	}
}
