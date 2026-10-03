package com.seoulchonnom.aggregate.inspection.store.jpo.converter;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

/**
 * 분류 스냅샷이 answers JSON 컬럼을 거쳐도 그대로 보존되는지 고정한다.
 */
class PropertyAnswerListConverterTest {
	private final PropertyAnswerListConverter converter = new PropertyAnswerListConverter();

	@Test
	void convertToDatabaseColumn_shouldRoundTripCategorySnapshot() {
		PropertyAnswer answer = new PropertyAnswer();
		answer.setQuestionId("INSPECTION_QUESTION-0001");
		answer.setAnswerType(QuestionAnswerType.TEXT);
		answer.setCategoryId("CATEGORY-1");
		answer.setCategoryName("채광·환기");
		answer.setCategorySortOrder(1);

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
