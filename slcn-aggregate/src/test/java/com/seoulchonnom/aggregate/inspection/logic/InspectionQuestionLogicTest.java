package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionQuestionException;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
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
	private final InspectionQuestionStore inspectionQuestionStore = mock(InspectionQuestionStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final InspectionQuestionLogic inspectionQuestionLogic = new InspectionQuestionLogic(
		inspectionQuestionStore, new InspectionQuestionMapper(), idGenerator);

	private static InspectionQuestionCdo cdo(QuestionAnswerType answerType, String content) {
		InspectionQuestionCdo cdo = new InspectionQuestionCdo();
		cdo.setAnswerType(answerType);
		cdo.setContent(content);
		cdo.setRequired(true);
		cdo.setSortOrder(1);
		return cdo;
	}

	private static InspectionQuestionContentUdo contentUdo(String content) {
		InspectionQuestionContentUdo udo = new InspectionQuestionContentUdo();
		udo.setContent(content);
		return udo;
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
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.registerInspectionQuestion(
			cdo(QuestionAnswerType.LONG_TEXT, "채광은 어떤가?"));

		assertThat(rdo.getQuestionId()).isEqualTo("INSPECTION_QUESTION-0001");
		assertThat(rdo.getCurrentVersionNo()).isEqualTo(1);
		assertThat(rdo.getContent()).isEqualTo("채광은 어떤가?");
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

	@Test
	void modifyInspectionQuestionContent_shouldAppendVersionAndKeepTheOldOne() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001",
			QuestionAnswerType.LONG_TEXT, true, 1);
		question.addVersion("거실 채광은 어떤가?", "v1 도움말", null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
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
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001",
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
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", QuestionAnswerType.TEXT,
			true, 1);
		question.addVersion("메모", null, null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
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
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", QuestionAnswerType.TEXT,
			true, 1);
		question.addVersion("메모", null, null, null);
		when(inspectionQuestionStore.findById("INSPECTION_QUESTION-0001")).thenReturn(question);
		echoSave();

		InspectionQuestionRdo rdo = inspectionQuestionLogic.changeInspectionQuestionStatus(
			"INSPECTION_QUESTION-0001", new InspectionQuestionStatusUdo(false));

		assertThat(rdo.isEnabled()).isFalse();
		assertThat(rdo.getCurrentVersionNo()).isEqualTo(1);
	}

	@Test
	void getInspectionQuestionVersions_shouldReturnWholeHistoryWithCurrentFlag() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", QuestionAnswerType.TEXT,
			true, 1);
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
		InspectionQuestion first = new InspectionQuestion("INSPECTION_QUESTION-0001", QuestionAnswerType.TEXT,
			true, 1);
		InspectionQuestion second = new InspectionQuestion("INSPECTION_QUESTION-0002", QuestionAnswerType.TEXT,
			true, 2);
		when(inspectionQuestionStore.findAllByIds(anyCollection())).thenReturn(List.of(first, second));

		inspectionQuestionLogic.modifyInspectionQuestionOrder(List.of(
			new InspectionQuestionOrderUdo("INSPECTION_QUESTION-0001", 5),
			new InspectionQuestionOrderUdo("INSPECTION_QUESTION-0002", 3)));

		assertThat(first.getSortOrder()).isEqualTo(5);
		assertThat(second.getSortOrder()).isEqualTo(3);
		verify(inspectionQuestionStore).saveAll(List.of(first, second));
	}
}
