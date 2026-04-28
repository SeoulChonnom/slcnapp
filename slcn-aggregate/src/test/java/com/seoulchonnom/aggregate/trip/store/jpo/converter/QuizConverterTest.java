package com.seoulchonnom.aggregate.trip.store.jpo.converter;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;

class QuizConverterTest {
	private final QuizConverter quizConverter = new QuizConverter();

	@Test
	void convertToDatabaseColumn_shouldSerializeCurrentQuizShape() {
		Quiz quiz = Quiz.builder()
			.title("Quiz Title")
			.correctOptionId("OPT-2")
			.answerTitle("Answer Title")
			.answerText("Answer Text")
			.errorTitle("Error Title")
			.errorText("Error Text")
			.options(List.of(
				Option.builder().id("OPT-1").text("wrong").build(),
				Option.builder().id("OPT-2").text("right").build()))
			.build();

		String json = quizConverter.convertToDatabaseColumn(quiz);

		assertThat(json).contains("\"correctOptionId\":\"OPT-2\"");
		assertThat(json).contains("\"options\"");
	}

	@Test
	void convertToEntityAttribute_shouldIgnoreLegacyFields() {
		String legacyJson = """
			{
			  \"title\": \"Quiz Title\",
			  \"correctOptionId\": \"OPT-2\",
			  \"answerTitle\": \"Answer Title\",
			  \"answerText\": \"Answer Text\",
			  \"errorTitle\": \"Error Title\",
			  \"errorText\": \"Error Text\",
			  \"tripId\": \"TRIP-1\",
			  \"options\": [
			    {\"id\": \"OPT-1\", \"text\": \"wrong\", \"sortOrder\": 1, \"tripId\": \"TRIP-1\"},
			    {\"id\": \"OPT-2\", \"text\": \"right\", \"sortOrder\": 2, \"tripId\": \"TRIP-1\"}
			  ]
			}
			""";

		Quiz quiz = quizConverter.convertToEntityAttribute(legacyJson);

		assertThat(quiz.getTitle()).isEqualTo("Quiz Title");
		assertThat(quiz.getCorrectOptionId()).isEqualTo("OPT-2");
		assertThat(quiz.getOptions()).extracting("id", "text")
			.containsExactly(tuple("OPT-1", "wrong"), tuple("OPT-2", "right"));
	}
}
