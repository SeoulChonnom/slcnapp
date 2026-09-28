package com.seoulchonnom.aggregate.inspection.store.jpo.converter;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

/**
 * 백필 전 과거 매물 행에는 categoryId/categoryName/categorySortOrder가 없는 구버전 JSON이 저장돼 있다.
 * JsonUtil이 FAIL_ON_UNKNOWN_PROPERTIES를 꺼 두었지만, 반대 방향(JSON에 없는 필드를 읽는 것)도
 * 실패하지 않고 null로 채워지는지는 별도로 확인해야 한다 - 여기서 그 계약을 고정한다.
 */
class PropertyAnswerListConverterTest {
	private final PropertyAnswerListConverter converter = new PropertyAnswerListConverter();

	@Test
	void convertToEntityAttribute_shouldFillCategoryFieldsAsNullForLegacyJson() {
		String legacyJson = """
			[
			  {
			    "questionId": "INSPECTION_QUESTION-0001",
			    "questionVersionNo": 1,
			    "questionContent": "방향은?",
			    "questionDescription": null,
			    "answerType": "SINGLE_SELECT",
			    "required": true,
			    "sortOrder": 1,
			    "unit": null,
			    "choiceOptions": [
			      {"code": "SOUTH", "label": "남향", "sortOrder": 1}
			    ],
			    "textValue": null,
			    "booleanValue": null,
			    "numberValue": null,
			    "ratingValue": null,
			    "selectedCodes": ["SOUTH"],
			    "answered": true
			  }
			]
			""";

		List<PropertyAnswer> answers = converter.convertToEntityAttribute(legacyJson);

		assertThat(answers).hasSize(1);
		PropertyAnswer answer = answers.get(0);
		assertThat(answer.getQuestionId()).isEqualTo("INSPECTION_QUESTION-0001");
		assertThat(answer.getAnswerType()).isEqualTo(QuestionAnswerType.SINGLE_SELECT);
		assertThat(answer.isAnswered()).isTrue();
		assertThat(answer.getCategoryId()).isNull();
		assertThat(answer.getCategoryName()).isNull();
		assertThat(answer.getCategorySortOrder()).isNull();
	}

	@Test
	void convertToDatabaseColumn_shouldRoundTripCategorySnapshot() {
		PropertyAnswer answer = new PropertyAnswer();
		answer.setQuestionId("INSPECTION_QUESTION-0001");
		answer.setAnswerType(QuestionAnswerType.TEXT);
		answer.assignCategory("CATEGORY-1", "채광·환기", 1);

		String json = converter.convertToDatabaseColumn(List.of(answer));
		List<PropertyAnswer> roundTripped = converter.convertToEntityAttribute(json);

		assertThat(roundTripped).hasSize(1);
		assertThat(roundTripped.get(0).getCategoryId()).isEqualTo("CATEGORY-1");
		assertThat(roundTripped.get(0).getCategoryName()).isEqualTo("채광·환기");
		assertThat(roundTripped.get(0).getCategorySortOrder()).isEqualTo(1);
	}

	@Test
	void convertToEntityAttribute_shouldReturnEmptyListForBlankColumn() {
		assertThat(converter.convertToEntityAttribute(null)).isEmpty();
		assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
	}
}
