package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerRdo;

class PropertyAnswerMapperTest {
	private final PropertyAnswerMapper propertyAnswerMapper = new PropertyAnswerMapper();

	/**
	 * v1 "방향은?" → v2 "주된 방향은?"으로 한 번 고쳐진 질문.
	 */
	private static InspectionQuestion question() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001",
			QuestionAnswerType.SINGLE_SELECT, true, 3);
		question.addVersion("방향은?", "v1 도움말", List.of(new QuestionChoice("SOUTH", "남향", 1)), "방위");
		question.addVersion("주된 방향은?", "v2 도움말", List.of(new QuestionChoice("SOUTH", "남향", 1)), "방위");
		return question;
	}

	@Test
	void toPropertyAnswer_shouldCopyQuestionSnapshot() {
		InspectionQuestion question = question();
		QuestionVersion current = question.currentVersion().orElseThrow();

		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question, current);

		assertThat(answer.getQuestionId()).isEqualTo("INSPECTION_QUESTION-0001");
		assertThat(answer.getQuestionVersionNo()).isEqualTo(2);
		assertThat(answer.getQuestionContent()).isEqualTo("주된 방향은?");
		assertThat(answer.getAnswerType()).isEqualTo(QuestionAnswerType.SINGLE_SELECT);
		assertThat(answer.isRequired()).isTrue();
		assertThat(answer.getSortOrder()).isEqualTo(3);
		assertThat(answer.getUnit()).isEqualTo("방위");
		assertThat(answer.getChoiceOptions()).extracting(QuestionChoice::getCode).containsExactly("SOUTH");
		assertThat(answer.isAnswered()).isFalse();
	}

	@Test
	void toPropertyAnswer_shouldSnapshotUnitSoLaterUnitChangeDoesNotLeak() {
		InspectionQuestion question = question();
		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question,
			question.currentVersion().orElseThrow());

		question.addVersion("주된 방향은?", "v3", List.of(), "도");

		assertThat(answer.getUnit()).isEqualTo("방위");
	}

	@Test
	void toPropertyAnswer_shouldNotShareChoiceListWithVersion() {
		InspectionQuestion question = question();
		QuestionVersion current = question.currentVersion().orElseThrow();

		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question, current);
		answer.getChoiceOptions().clear();

		assertThat(current.getChoices()).hasSize(1);
	}

	@Test
	void toPropertyAnswerRdo_shouldRenderFromSnapshotNotFromCurrentQuestion() {
		InspectionQuestion question = question();
		// v1 문구에 답한 오래된 기록
		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question, question.findVersion(1).orElseThrow());
		question.changeEnabled(false);

		PropertyAnswerRdo rdo = propertyAnswerMapper.toPropertyAnswerRdo(answer, question);

		assertThat(rdo.getQuestion()).isEqualTo("방향은?");
		assertThat(rdo.getQuestionVersionNo()).isEqualTo(1);
		assertThat(rdo.getIsCurrentVersion()).isFalse();
		assertThat(rdo.getQuestionEnabled()).isFalse();
	}

	@Test
	void toPropertyAnswerRdo_shouldMarkCurrentVersion() {
		InspectionQuestion question = question();
		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question,
			question.currentVersion().orElseThrow());

		PropertyAnswerRdo rdo = propertyAnswerMapper.toPropertyAnswerRdo(answer, question);

		assertThat(rdo.getIsCurrentVersion()).isTrue();
	}

	@Test
	void toPropertyAnswerRdo_shouldStillRenderWhenQuestionMasterIsMissing() {
		InspectionQuestion question = question();
		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question,
			question.currentVersion().orElseThrow());

		PropertyAnswerRdo rdo = propertyAnswerMapper.toPropertyAnswerRdo(answer, null);

		assertThat(rdo.getQuestion()).isEqualTo("주된 방향은?");
		assertThat(rdo.getUnit()).isEqualTo("방위");
		assertThat(rdo.getChoiceOptions()).hasSize(1);
		assertThat(rdo.getIsCurrentVersion()).isNull();
		assertThat(rdo.getQuestionEnabled()).isNull();
	}
}
