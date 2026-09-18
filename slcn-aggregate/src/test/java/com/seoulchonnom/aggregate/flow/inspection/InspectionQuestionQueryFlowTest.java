package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.logic.ViewedPropertyLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionQuestionMapper;
import com.seoulchonnom.spec.inspection.mapper.PropertyAnswerMapper;

class InspectionQuestionQueryFlowTest {
	private final InspectionQuestionStore inspectionQuestionStore = mock(InspectionQuestionStore.class);
	private final ViewedPropertyStore viewedPropertyStore = mock(ViewedPropertyStore.class);
	private final InspectionQuestionQueryFlow inspectionQuestionQueryFlow = new InspectionQuestionQueryFlow(
		inspectionQuestionStore, viewedPropertyStore, new InspectionQuestionMapper());
	private final ViewedPropertyLogic viewedPropertyLogic = new ViewedPropertyLogic(viewedPropertyStore,
		new PropertyAnswerMapper());

	private static InspectionQuestion question(String id) {
		InspectionQuestion question = new InspectionQuestion(id, QuestionAnswerType.TEXT, true, 1);
		question.addVersion("v1 문구", null, null, null);
		return question;
	}

	/**
	 * v1 문구로 답한 매물과 v2 문구로 답한 매물을 각각 만든다.
	 */
	private ViewedProperty propertyAnsweringVersion(InspectionQuestion question, String propertyId) {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);
		property.setId(propertyId);
		viewedPropertyLogic.materializeAnswers(property, List.of(question));
		return property;
	}

	@Test
	void getInspectionQuestions_shouldLeaveAnswerCountNullWhenNotRequested() {
		when(inspectionQuestionStore.findAllEnabled()).thenReturn(List.of(question("q1")));

		List<InspectionQuestionRdo> questions = inspectionQuestionQueryFlow.getInspectionQuestions(false, false);

		assertThat(questions.get(0).getAnswerCount()).isNull();
		// 일반 사용자 흐름은 전건 스캔을 타지 않는다
		verify(viewedPropertyStore, never()).findAll();
	}

	@Test
	void getInspectionQuestions_shouldCountAnswersWhenRequested() {
		InspectionQuestion question = question("q1");
		when(inspectionQuestionStore.findAllEnabled()).thenReturn(List.of(question));
		when(viewedPropertyStore.findAll()).thenReturn(List.of(
			propertyAnsweringVersion(question, "p1"),
			propertyAnsweringVersion(question, "p2")));

		List<InspectionQuestionRdo> questions = inspectionQuestionQueryFlow.getInspectionQuestions(false, true);

		assertThat(questions.get(0).getAnswerCount()).isEqualTo(2);
	}

	@Test
	void getInspectionQuestions_shouldReportZeroForUnusedQuestion() {
		when(inspectionQuestionStore.findAllEnabled()).thenReturn(List.of(question("q1")));
		when(viewedPropertyStore.findAll()).thenReturn(List.of());

		assertThat(inspectionQuestionQueryFlow.getInspectionQuestions(false, true).get(0).getAnswerCount())
			.isZero();
	}

	@Test
	void getInspectionQuestions_shouldIncludeDisabledWhenAsked() {
		when(inspectionQuestionStore.findAll()).thenReturn(List.of(question("q1"), question("q2")));

		assertThat(inspectionQuestionQueryFlow.getInspectionQuestions(true, false)).hasSize(2);
		verify(inspectionQuestionStore, never()).findAllEnabled();
	}

	@Test
	void getInspectionQuestionVersions_shouldSplitCountsByVersion() {
		InspectionQuestion question = question("q1");
		ViewedProperty onV1 = propertyAnsweringVersion(question, "p1");
		question.addVersion("v2 문구", null, null, null);
		ViewedProperty onV2 = propertyAnsweringVersion(question, "p2");

		when(inspectionQuestionStore.findById("q1")).thenReturn(question);
		when(viewedPropertyStore.findAll()).thenReturn(List.of(onV1, onV2));

		List<InspectionQuestionVersionRdo> versions =
			inspectionQuestionQueryFlow.getInspectionQuestionVersions("q1");

		assertThat(versions).hasSize(2);
		assertThat(versions.get(0).getVersionNo()).isEqualTo(1);
		assertThat(versions.get(0).getAnswerCount()).isEqualTo(1);
		assertThat(versions.get(0).isCurrent()).isFalse();
		assertThat(versions.get(1).getVersionNo()).isEqualTo(2);
		assertThat(versions.get(1).getAnswerCount()).isEqualTo(1);
		assertThat(versions.get(1).isCurrent()).isTrue();
	}

	@Test
	void getInspectionQuestionVersions_shouldIgnoreAnswersOfOtherQuestions() {
		InspectionQuestion target = question("q1");
		InspectionQuestion other = question("q2");
		when(inspectionQuestionStore.findById("q1")).thenReturn(target);
		when(viewedPropertyStore.findAll()).thenReturn(List.of(propertyAnsweringVersion(other, "p1")));

		assertThat(inspectionQuestionQueryFlow.getInspectionQuestionVersions("q1").get(0).getAnswerCount())
			.isZero();
	}
}
