package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.exception.InspectionAnswerRequiredException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidPropertyAnswerException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidViewedPropertyException;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;
import com.seoulchonnom.spec.inspection.mapper.PropertyAnswerMapper;

class ViewedPropertyLogicTest {
	private final ViewedPropertyStore viewedPropertyStore = mock(ViewedPropertyStore.class);
	private final ViewedPropertyLogic viewedPropertyLogic = new ViewedPropertyLogic(viewedPropertyStore,
		new PropertyAnswerMapper());

	private static ViewedProperty property() {
		return new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동 1203호", 1);
	}

	private static InspectionQuestion question(String id, QuestionAnswerType answerType, boolean required,
		List<QuestionChoice> choices) {
		InspectionQuestion question = new InspectionQuestion(id, answerType, required, 1);
		question.addVersion("질문 " + id, null, choices, answerType == QuestionAnswerType.NUMBER ? "만원" : null);
		return question;
	}

	private static PropertyAnswerUdo answerUdo(String questionId) {
		PropertyAnswerUdo udo = new PropertyAnswerUdo();
		udo.setQuestionId(questionId);
		return udo;
	}

	private static ViewedPropertyUdo propertyUdo(String complexName, String name) {
		ViewedPropertyUdo udo = new ViewedPropertyUdo();
		udo.setComplexName(complexName);
		udo.setName(name);
		return udo;
	}

	@Test
	void materializeAnswers_shouldSnapshotEnabledQuestionsAndCounts() {
		ViewedProperty property = property();

		viewedPropertyLogic.materializeAnswers(property, List.of(
			question("q1", QuestionAnswerType.LONG_TEXT, true, null),
			question("q2", QuestionAnswerType.NUMBER, false, null)));

		assertThat(property.getAnswers()).hasSize(2);
		assertThat(property.getRequiredAnswerCount()).isEqualTo(1);
		assertThat(property.getUnansweredRequiredCount()).isEqualTo(1);
		assertThat(property.findAnswer("q2").orElseThrow().getUnit()).isEqualTo("만원");
	}

	@Test
	void materializeAnswers_shouldSkipQuestionWithoutVersion() {
		ViewedProperty property = property();
		InspectionQuestion broken = new InspectionQuestion("q9", QuestionAnswerType.TEXT, true, 1);

		viewedPropertyLogic.materializeAnswers(property, List.of(broken));

		assertThat(property.getAnswers()).isEmpty();
		assertThat(property.getUnansweredRequiredCount()).isZero();
	}

	@Test
	void materializeAnswers_shouldAllowNoEnabledQuestion() {
		ViewedProperty property = property();

		viewedPropertyLogic.materializeAnswers(property, List.of());

		assertThat(property.getAnswers()).isEmpty();
		assertThatCode(() -> viewedPropertyLogic.validateCompletable(withInterest(property)))
			.doesNotThrowAnyException();
	}

	private ViewedProperty withInterest(ViewedProperty property) {
		property.setInterestLevel(4);
		return property;
	}

	@Test
	void applyAnswers_shouldKeepUntouchedAnswers() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(
			question("q1", QuestionAnswerType.LONG_TEXT, true, null),
			question("q2", QuestionAnswerType.LONG_TEXT, true, null)));

		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setTextValue("오후에도 밝았다");
		viewedPropertyLogic.applyAnswers(property, List.of(udo));

		assertThat(property.findAnswer("q1").orElseThrow().isAnswered()).isTrue();
		assertThat(property.findAnswer("q2").orElseThrow().isAnswered()).isFalse();
		assertThat(property.getUnansweredRequiredCount()).isEqualTo(1);
	}

	@Test
	void applyAnswers_shouldRejectUnknownQuestionId() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)));

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(answerUdo("q9"))))
			.isInstanceOf(InspectionQuestionNotFoundException.class);
	}

	@Test
	void applyAnswers_shouldRejectValueOfAnotherType() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.NUMBER, true, null)));
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setNumberValue(BigDecimal.TEN);
		udo.setTextValue("설명");

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectRatingOutOfRange() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.RATING, true, null)));
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setRatingValue(6);

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectUnknownChoiceCode() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.SINGLE_SELECT,
			true, List.of(new QuestionChoice("SOUTH", "남향", 1)))));
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setSelectedCodes(List.of("NORTH"));

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectTwoCodesForSingleSelect() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.SINGLE_SELECT,
			true, List.of(new QuestionChoice("SOUTH", "남향", 1), new QuestionChoice("EAST", "동향", 2)))));
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setSelectedCodes(List.of("SOUTH", "EAST"));

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectDuplicatedCodeForMultiSelect() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.MULTI_SELECT,
			true, List.of(new QuestionChoice("SOUTH", "남향", 1)))));
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setSelectedCodes(List.of("SOUTH", "SOUTH"));

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldClearValueWhenEmptyTextGiven() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)));
		PropertyAnswerUdo filled = answerUdo("q1");
		filled.setTextValue("밝음");
		viewedPropertyLogic.applyAnswers(property, List.of(filled));
		assertThat(property.getUnansweredRequiredCount()).isZero();

		PropertyAnswerUdo cleared = answerUdo("q1");
		cleared.setTextValue("");
		viewedPropertyLogic.applyAnswers(property, List.of(cleared));

		assertThat(property.getUnansweredRequiredCount()).isEqualTo(1);
	}

	@Test
	void applyUpdate_shouldRequireComplexNameAndName() {
		assertThatThrownBy(() -> viewedPropertyLogic.applyUpdate(property(), propertyUdo("  ", "101동")))
			.isInstanceOf(InvalidViewedPropertyException.class);
		assertThatThrownBy(() -> viewedPropertyLogic.applyUpdate(property(), propertyUdo("트리마제", " ")))
			.isInstanceOf(InvalidViewedPropertyException.class);
	}

	@Test
	void applyUpdate_shouldCollapseComplexNameWhitespace() {
		ViewedProperty property = property();

		viewedPropertyLogic.applyUpdate(property, propertyUdo("  트리마제   1차 ", "101동"));

		assertThat(property.getComplexName()).isEqualTo("트리마제 1차");
	}

	@Test
	void applyUpdate_shouldRejectInterestLevelOutOfRange() {
		ViewedPropertyUdo udo = propertyUdo("트리마제", "101동");
		udo.setInterestLevel(0);

		assertThatThrownBy(() -> viewedPropertyLogic.applyUpdate(property(), udo))
			.isInstanceOf(InvalidViewedPropertyException.class);
	}

	@Test
	void validateCompletable_shouldReportMissingInterestLevel() {
		ViewedProperty property = property();

		assertThatThrownBy(() -> viewedPropertyLogic.validateCompletable(property))
			.isInstanceOf(InvalidViewedPropertyException.class)
			.hasMessageContaining("interestLevel");
	}

	@Test
	void validateCompletable_shouldReportUnansweredRequiredQuestionIds() {
		ViewedProperty property = withInterest(property());
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)));

		assertThatThrownBy(() -> viewedPropertyLogic.validateCompletable(property))
			.isInstanceOf(InspectionAnswerRequiredException.class)
			.hasMessageContaining("q1");
	}

	@Test
	void revalidateIfCompleted_shouldRejectEmptyingARequiredAnswer() {
		ViewedProperty property = withInterest(property());
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)));
		PropertyAnswerUdo filled = answerUdo("q1");
		filled.setTextValue("밝음");
		viewedPropertyLogic.applyAnswers(property, List.of(filled));
		property.changeStatus(InspectionStatus.COMPLETED);

		PropertyAnswerUdo cleared = answerUdo("q1");
		cleared.setTextValue("");
		viewedPropertyLogic.applyAnswers(property, List.of(cleared));

		// 필수 문항이 빈 COMPLETED 매물이 남으면 어떤 화면에서도 경고가 뜨지 않는다
		assertThatThrownBy(() -> viewedPropertyLogic.revalidateIfCompleted(property))
			.isInstanceOf(InspectionAnswerRequiredException.class);
	}

	@Test
	void revalidateIfCompleted_shouldDoNothingForDraft() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)));

		assertThatCode(() -> viewedPropertyLogic.revalidateIfCompleted(property)).doesNotThrowAnyException();
	}

	@Test
	void findUnansweredRequired_shouldIgnoreOptionalQuestions() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(
			question("q1", QuestionAnswerType.TEXT, false, null),
			question("q2", QuestionAnswerType.TEXT, true, null)));

		assertThat(viewedPropertyLogic.findUnansweredRequired(property))
			.extracting(PropertyAnswer::getQuestionId).containsExactly("q2");
	}
}
