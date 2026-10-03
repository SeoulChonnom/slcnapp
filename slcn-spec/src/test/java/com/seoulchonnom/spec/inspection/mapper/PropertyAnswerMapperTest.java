package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerRdo;

class PropertyAnswerMapperTest {
	private static final InspectionQuestionCategory CATEGORY = new InspectionQuestionCategory("CATEGORY-1", "기본", 1);

	private final PropertyAnswerMapper propertyAnswerMapper = new PropertyAnswerMapper();

	/**
	 * v1 "방향은?" → v2 "주된 방향은?"으로 한 번 고쳐진 질문.
	 */
	private static InspectionQuestion question() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0001", "CATEGORY-1",
			QuestionAnswerType.SINGLE_SELECT, true, 3);
		question.addVersion("방향은?", "v1 도움말", List.of(new QuestionChoice("SOUTH", "남향", 1)), "방위");
		question.addVersion("주된 방향은?", "v2 도움말", List.of(new QuestionChoice("SOUTH", "남향", 1)), "방위");
		return question;
	}

	@Test
	void toPropertyAnswer_shouldCopyQuestionSnapshot() {
		InspectionQuestion question = question();
		QuestionVersion current = question.currentVersion().orElseThrow();

		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question, current, CATEGORY);

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
			question.currentVersion().orElseThrow(), CATEGORY);

		question.addVersion("주된 방향은?", "v3", List.of(), "도");

		assertThat(answer.getUnit()).isEqualTo("방위");
	}

	@Test
	void toPropertyAnswer_shouldNotShareChoiceListWithVersion() {
		InspectionQuestion question = question();
		QuestionVersion current = question.currentVersion().orElseThrow();

		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question, current, CATEGORY);
		answer.getChoiceOptions().clear();

		assertThat(current.getChoices()).hasSize(1);
	}

	@Test
	void toPropertyAnswerRdo_shouldRenderFromSnapshotNotFromCurrentQuestion() {
		InspectionQuestion question = question();
		// v1 문구에 답한 오래된 기록
		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question,
			question.findVersion(1).orElseThrow(), CATEGORY);
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
			question.currentVersion().orElseThrow(), CATEGORY);

		PropertyAnswerRdo rdo = propertyAnswerMapper.toPropertyAnswerRdo(answer, question);

		assertThat(rdo.getIsCurrentVersion()).isTrue();
	}

	@Test
	void toPropertyAnswerRdo_shouldStillRenderWhenQuestionMasterIsMissing() {
		InspectionQuestion question = question();
		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question,
			question.currentVersion().orElseThrow(), CATEGORY);

		PropertyAnswerRdo rdo = propertyAnswerMapper.toPropertyAnswerRdo(answer, null);

		assertThat(rdo.getQuestion()).isEqualTo("주된 방향은?");
		assertThat(rdo.getUnit()).isEqualTo("방위");
		assertThat(rdo.getChoiceOptions()).hasSize(1);
		assertThat(rdo.getIsCurrentVersion()).isNull();
		assertThat(rdo.getQuestionEnabled()).isNull();
	}

	@Test
	void toPropertyAnswer_shouldSnapshotCategoryFromGivenCategory() {
		InspectionQuestion question = question();
		question.setCategoryId("CATEGORY-1");
		InspectionQuestionCategory category = new InspectionQuestionCategory("CATEGORY-1", "채광·환기", 1);

		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question,
			question.currentVersion().orElseThrow(), category);

		assertThat(answer.getCategoryId()).isEqualTo("CATEGORY-1");
		assertThat(answer.getCategoryName()).isEqualTo("채광·환기");
		assertThat(answer.getCategorySortOrder()).isEqualTo(1);
	}

	/**
	 * 렌더링은 답변 스냅샷만 본다 - question의 categoryId가 바뀌어도(분류 이동) 과거 기록은 그대로다.
	 */
	@Test
	void toPropertyAnswerRdo_shouldRenderCategoryFromSnapshotNotFromCurrentQuestion() {
		InspectionQuestion question = question();
		question.setCategoryId("CATEGORY-1");
		InspectionQuestionCategory category = new InspectionQuestionCategory("CATEGORY-1", "채광·환기", 1);
		PropertyAnswer answer = propertyAnswerMapper.toPropertyAnswer(question,
			question.currentVersion().orElseThrow(), category);

		// 이후 다른 분류로 이동했지만 이미 만든 스냅샷은 바뀌지 않는다
		question.setCategoryId("CATEGORY-2");

		PropertyAnswerRdo rdo = propertyAnswerMapper.toPropertyAnswerRdo(answer, question);

		assertThat(rdo.getCategoryId()).isEqualTo("CATEGORY-1");
		assertThat(rdo.getCategoryName()).isEqualTo("채광·환기");
		assertThat(rdo.getCategorySortOrder()).isEqualTo(1);
	}
}
