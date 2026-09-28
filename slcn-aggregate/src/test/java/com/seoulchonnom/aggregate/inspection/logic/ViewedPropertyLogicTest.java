package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.exception.InspectionAnswerRequiredException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidPropertyAnswerException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidViewedPropertyException;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
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
			question("q2", QuestionAnswerType.NUMBER, false, null)), Map.of());

		assertThat(property.getAnswers()).hasSize(2);
		assertThat(property.getRequiredAnswerCount()).isEqualTo(1);
		assertThat(property.getUnansweredRequiredCount()).isEqualTo(1);
		assertThat(property.findAnswer("q2").orElseThrow().getUnit()).isEqualTo("만원");
	}

	@Test
	void materializeAnswers_shouldSkipQuestionWithoutVersion() {
		ViewedProperty property = property();
		InspectionQuestion broken = new InspectionQuestion("q9", QuestionAnswerType.TEXT, true, 1);

		viewedPropertyLogic.materializeAnswers(property, List.of(broken), Map.of());

		assertThat(property.getAnswers()).isEmpty();
		assertThat(property.getUnansweredRequiredCount()).isZero();
	}

	@Test
	void materializeAnswers_shouldAllowNoEnabledQuestion() {
		ViewedProperty property = property();

		viewedPropertyLogic.materializeAnswers(property, List.of(), Map.of());

		assertThat(property.getAnswers()).isEmpty();
		assertThatCode(() -> viewedPropertyLogic.validateCompletable(withInterest(property)))
			.doesNotThrowAnyException();
	}

	private ViewedProperty withInterest(ViewedProperty property) {
		property.setInterestLevel(4);
		return property;
	}

	/**
	 * 계획 §2: 저장 순서도 분류 순서로 맞춘다. q1은 sortOrder가 앞서지만 분류 자체 순서(B=1 < A=2)가
	 * 우선이라 q2가 먼저 와야 한다.
	 */
	@Test
	void materializeAnswers_shouldStoreAnswersSortedByCategoryThenQuestion() {
		ViewedProperty property = property();
		InspectionQuestionCategory categoryA = new InspectionQuestionCategory("CATEGORY-A", "채광", 2);
		InspectionQuestionCategory categoryB = new InspectionQuestionCategory("CATEGORY-B", "구조", 1);
		InspectionQuestion q1 = question("q1", QuestionAnswerType.TEXT, false, null);
		q1.setCategoryId("CATEGORY-A");
		InspectionQuestion q2 = question("q2", QuestionAnswerType.TEXT, false, null);
		q2.setCategoryId("CATEGORY-B");

		viewedPropertyLogic.materializeAnswers(property, List.of(q1, q2),
			Map.of("CATEGORY-A", categoryA, "CATEGORY-B", categoryB));

		assertThat(property.getAnswers()).extracting(PropertyAnswer::getQuestionId)
			.containsExactly("q2", "q1");
	}

	/**
	 * 스냅샷은 생성 시점 값을 복사한다(계획 §1) - 이후 분류 이름/순서가 바뀌어도 이미 만든 매물은
	 * 그대로다. 백필과 같은 원칙이라 renaming이 기존 매물을 건드리면 안 된다.
	 */
	@Test
	void materializeAnswers_shouldNotBeAffectedByLaterCategoryRename() {
		ViewedProperty property = property();
		InspectionQuestionCategory category = new InspectionQuestionCategory("CATEGORY-A", "채광·환기", 1);
		InspectionQuestion q1 = question("q1", QuestionAnswerType.TEXT, false, null);
		q1.setCategoryId("CATEGORY-A");

		viewedPropertyLogic.materializeAnswers(property, List.of(q1), Map.of("CATEGORY-A", category));

		// 매물을 만든 뒤에 분류 이름이 바뀐다
		category.rename("채광");

		PropertyAnswer snapshot = property.findAnswer("q1").orElseThrow();
		assertThat(snapshot.getCategoryName()).isEqualTo("채광·환기");
	}

	@Test
	void applyAnswers_shouldKeepUntouchedAnswers() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(
			question("q1", QuestionAnswerType.LONG_TEXT, true, null),
			question("q2", QuestionAnswerType.LONG_TEXT, true, null)), Map.of());

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
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)), Map.of());

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(answerUdo("q9"))))
			.isInstanceOf(InspectionQuestionNotFoundException.class);
	}

	@Test
	void applyAnswers_shouldRejectValueOfAnotherType() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.NUMBER, true, null)), Map.of());
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setNumberValue(BigDecimal.TEN);
		udo.setTextValue("설명");

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectRatingOutOfRange() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.RATING, true, null)), Map.of());
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setRatingValue(6);

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectUnknownChoiceCode() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.SINGLE_SELECT,
			true, List.of(new QuestionChoice("SOUTH", "남향", 1)))), Map.of());
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setSelectedCodes(List.of("NORTH"));

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectTwoCodesForSingleSelect() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.SINGLE_SELECT,
			true, List.of(new QuestionChoice("SOUTH", "남향", 1), new QuestionChoice("EAST", "동향", 2)))), Map.of());
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setSelectedCodes(List.of("SOUTH", "EAST"));

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldRejectDuplicatedCodeForMultiSelect() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.MULTI_SELECT,
			true, List.of(new QuestionChoice("SOUTH", "남향", 1)))), Map.of());
		PropertyAnswerUdo udo = answerUdo("q1");
		udo.setSelectedCodes(List.of("SOUTH", "SOUTH"));

		assertThatThrownBy(() -> viewedPropertyLogic.applyAnswers(property, List.of(udo)))
			.isInstanceOf(InvalidPropertyAnswerException.class);
	}

	@Test
	void applyAnswers_shouldClearValueWhenEmptyTextGiven() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)), Map.of());
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

	/**
	 * viewed_property.complex_name / name이 varchar(200)이다.
	 * 앞에서 막지 않으면 flush 시점 제약 위반이 500으로 새어나간다.
	 */
	@Test
	void applyUpdate_shouldRejectNamesLongerThanColumn() {
		assertThatThrownBy(() -> viewedPropertyLogic.applyUpdate(property(),
			propertyUdo("가".repeat(201), "101동")))
			.isInstanceOf(InvalidViewedPropertyException.class);
		assertThatThrownBy(() -> viewedPropertyLogic.applyUpdate(property(),
			propertyUdo("트리마제", "가".repeat(201))))
			.isInstanceOf(InvalidViewedPropertyException.class);
	}

	@Test
	void applyUpdate_shouldAcceptNamesAtColumnLimit() {
		ViewedProperty property = property();

		viewedPropertyLogic.applyUpdate(property, propertyUdo("가".repeat(200), "나".repeat(200)));

		assertThat(property.getComplexName()).hasSize(200);
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
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)), Map.of());

		assertThatThrownBy(() -> viewedPropertyLogic.validateCompletable(property))
			.isInstanceOf(InspectionAnswerRequiredException.class)
			.hasMessageContaining("q1");
	}

	@Test
	void revalidateIfCompleted_shouldRejectEmptyingARequiredAnswer() {
		ViewedProperty property = withInterest(property());
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)), Map.of());
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
		viewedPropertyLogic.materializeAnswers(property, List.of(question("q1", QuestionAnswerType.TEXT, true, null)), Map.of());

		assertThatCode(() -> viewedPropertyLogic.revalidateIfCompleted(property)).doesNotThrowAnyException();
	}

	@Test
	void findUnansweredRequired_shouldIgnoreOptionalQuestions() {
		ViewedProperty property = property();
		viewedPropertyLogic.materializeAnswers(property, List.of(
			question("q1", QuestionAnswerType.TEXT, false, null),
			question("q2", QuestionAnswerType.TEXT, true, null)), Map.of());

		assertThat(viewedPropertyLogic.findUnansweredRequired(property))
			.extracting(PropertyAnswer::getQuestionId).containsExactly("q2");
	}
}
