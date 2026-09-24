package com.seoulchonnom.spec.inspection.entity;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;

class InspectionQuestionTest {
	private static InspectionQuestion question() {
		return new InspectionQuestion("INSPECTION_QUESTION-0001", QuestionAnswerType.SINGLE_SELECT, true, 1);
	}

	@Test
	void addVersion_shouldNumberFromOne() {
		InspectionQuestion question = question();

		QuestionVersion first = question.addVersion("방향은?", null, null, null);

		assertThat(first.getVersionNo()).isEqualTo(1);
		assertThat(question.getCurrentVersionNo()).isEqualTo(1);
	}

	@Test
	void addVersion_shouldNotModifyPreviousVersion() {
		InspectionQuestion question = question();
		question.addVersion("거실 채광은 어떤가?", "v1 도움말", List.of(new QuestionChoice("A", "밝음", 1)), "점");

		question.addVersion("거실 및 방의 채광은 어떤가?", "v2 도움말", List.of(new QuestionChoice("B", "어두움", 1)), "단계");

		QuestionVersion v1 = question.findVersion(1).orElseThrow();
		assertThat(v1.getContent()).isEqualTo("거실 채광은 어떤가?");
		assertThat(v1.getDescription()).isEqualTo("v1 도움말");
		assertThat(v1.getUnit()).isEqualTo("점");
		assertThat(v1.getChoices()).extracting(QuestionChoice::getCode).containsExactly("A");
		assertThat(question.getCurrentVersionNo()).isEqualTo(2);
	}

	@Test
	void addVersion_shouldNotShareChoiceListWithCaller() {
		InspectionQuestion question = question();
		List<QuestionChoice> choices = List.of(new QuestionChoice("A", "밝음", 1));

		QuestionVersion version = question.addVersion("방향은?", null, choices, null);
		version.getChoices().clear();

		assertThat(choices).hasSize(1);
	}

	@Test
	void currentVersion_shouldPointAtTheLastAddedVersion() {
		InspectionQuestion question = question();
		question.addVersion("v1", null, null, null);
		question.addVersion("v2", null, null, null);

		assertThat(question.currentVersion().orElseThrow().getContent()).isEqualTo("v2");
	}

	@Test
	void currentVersion_shouldBeEmptyBeforeAnyVersionIsAdded() {
		assertThat(question().currentVersion()).isEmpty();
	}

	@Test
	void changeEnabled_shouldNotTouchVersions() {
		InspectionQuestion question = question();
		question.addVersion("v1", null, null, null);

		question.changeEnabled(false);
		question.changePolicy(false, 9);

		assertThat(question.getVersions()).hasSize(1);
		assertThat(question.getCurrentVersionNo()).isEqualTo(1);
	}
}
