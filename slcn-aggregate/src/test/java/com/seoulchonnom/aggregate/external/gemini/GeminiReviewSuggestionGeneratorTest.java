package com.seoulchonnom.aggregate.external.gemini;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;

import com.seoulchonnom.aggregate.inspection.exception.ReviewSuggestionUnavailableException;
import com.google.genai.types.GenerateContentConfig;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestion;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionPrompt;

class GeminiReviewSuggestionGeneratorTest {
	@Test
	void parse_shouldReadAllFields() {
		ReviewSuggestion suggestion = GeminiReviewSuggestionGenerator.parse(
			"{\"oneLineReview\":\"조용한 동네\",\"pros\":\"- 한강뷰\",\"cons\":\"- 주차 불편\",\"tags\":[\"조용함\",\"한강\"]}");

		assertThat(suggestion.oneLineReview()).isEqualTo("조용한 동네");
		assertThat(suggestion.pros()).isEqualTo("- 한강뷰");
		assertThat(suggestion.cons()).isEqualTo("- 주차 불편");
		assertThat(suggestion.tags()).containsExactly("조용함", "한강");
	}

	@Test
	void parse_shouldTolerateMissingFieldsAndNonTextTags() {
		ReviewSuggestion suggestion = GeminiReviewSuggestionGenerator.parse("{\"tags\":[\"a\",1,null]}");

		assertThat(suggestion.oneLineReview()).isNull();
		assertThat(suggestion.pros()).isNull();
		assertThat(suggestion.cons()).isNull();
		assertThat(suggestion.tags()).containsExactly("a");
	}

	@Test
	void parse_shouldMapBrokenInputToUnavailable() {
		assertThatThrownBy(() -> GeminiReviewSuggestionGenerator.parse("not json {"))
			.isInstanceOf(ReviewSuggestionUnavailableException.class);
		assertThatThrownBy(() -> GeminiReviewSuggestionGenerator.parse("[1,2]"))
			.isInstanceOf(ReviewSuggestionUnavailableException.class);
		assertThatThrownBy(() -> GeminiReviewSuggestionGenerator.parse(" "))
			.isInstanceOf(ReviewSuggestionUnavailableException.class);
		assertThatThrownBy(() -> GeminiReviewSuggestionGenerator.parse(null))
			.isInstanceOf(ReviewSuggestionUnavailableException.class);
	}

	private GenerateContentConfig configOf(String thinkingLevel) {
		return new GeminiReviewSuggestionGenerator((GeminiReviewSuggestionGenerator.ModelCaller)null, "m", null, 20,
			thinkingLevel)
			.config(new ReviewSuggestionPrompt("지시", "기록"));
	}

	@Test
	void config_shouldSendThinkingLevelWhenSet() {
		assertThat(configOf("low").thinkingConfig().get().thinkingLevel().get().toString()).isEqualTo("LOW");
	}

	@Test
	void config_shouldOmitThinkingConfigWhenBlank() {
		assertThat(configOf("").thinkingConfig()).isEmpty();
		assertThat(configOf(null).thinkingConfig()).isEmpty();
	}

	@Test
	void constructor_shouldRejectUnknownThinkingLevel() {
		assertThatThrownBy(() -> configOf("TURBO")).isInstanceOf(IllegalArgumentException.class);
	}

	private static final String OK_JSON = "{\"oneLineReview\":\"좋음\",\"pros\":\"\",\"cons\":\"\",\"tags\":[]}";
	private static final ReviewSuggestionPrompt PROMPT = new ReviewSuggestionPrompt("지시", "기록");

	/**
	 * 모델 이름별 응답/예외를 정해 두고, 호출된 모델 순서와 넘어온 설정을 기록한다.
	 */
	private static class FakeCaller implements GeminiReviewSuggestionGenerator.ModelCaller {
		final List<String> calledModels = new ArrayList<>();
		final List<GenerateContentConfig> configs = new ArrayList<>();
		private final Function<String, Object> behavior;

		FakeCaller(Function<String, Object> behavior) {
			this.behavior = behavior;
		}

		@Override
		public String call(String model, String content, GenerateContentConfig config) {
			calledModels.add(model);
			configs.add(config);
			Object result = behavior.apply(model);
			if (result instanceof RuntimeException e) {
				throw e;
			}
			return (String)result;
		}
	}

	private GeminiReviewSuggestionGenerator generator(FakeCaller caller, String fallback) {
		return new GeminiReviewSuggestionGenerator(caller, "primary", fallback, 15, "LOW");
	}

	private static ApiException api(int code) {
		return new ApiException(code, "STATUS", "message");
	}

	@Test
	void generate_shouldNotCallFallbackWhenPrimarySucceeds() {
		FakeCaller caller = new FakeCaller(model -> OK_JSON);

		ReviewSuggestion result = generator(caller, "fallback").generate(PROMPT);

		assertThat(result.oneLineReview()).isEqualTo("좋음");
		assertThat(caller.calledModels).containsExactly("primary");
	}

	@Test
	void generate_shouldUseFallbackOnRetryableFailures() {
		List<Object> failures = List.of(api(503), api(500), api(429), new GenAiIOException("timeout"), "not json",
			"", "[1]");
		for (Object failure : failures) {
			FakeCaller caller = new FakeCaller(model -> "primary".equals(model) ? failure : OK_JSON);

			ReviewSuggestion result = generator(caller, "fallback").generate(PROMPT);

			assertThat(result.oneLineReview()).as("failure=%s", failure).isEqualTo("좋음");
			assertThat(caller.calledModels).containsExactly("primary", "fallback");
			assertThat(caller.configs.get(1)).isSameAs(caller.configs.get(0));
		}
	}

	@Test
	void generate_shouldNotFallBackOnConfigurationErrors() {
		for (int code : new int[] {400, 401, 403, 404}) {
			FakeCaller caller = new FakeCaller(model -> api(code));

			assertThatThrownBy(() -> generator(caller, "fallback").generate(PROMPT))
				.isInstanceOf(ReviewSuggestionUnavailableException.class);
			assertThat(caller.calledModels).containsExactly("primary");
		}
	}

	@Test
	void generate_shouldFailWhenFallbackAlsoFails() {
		FakeCaller caller = new FakeCaller(model -> "primary".equals(model) ? api(503) : "not json");

		assertThatThrownBy(() -> generator(caller, "fallback").generate(PROMPT))
			.isInstanceOf(ReviewSuggestionUnavailableException.class);
		assertThat(caller.calledModels).containsExactly("primary", "fallback");
	}

	@Test
	void generate_shouldFailWhenFallbackFailsWithConfigurationError() {
		FakeCaller caller = new FakeCaller(model -> "primary".equals(model) ? api(503) : api(404));

		assertThatThrownBy(() -> generator(caller, "fallback").generate(PROMPT))
			.isInstanceOf(ReviewSuggestionUnavailableException.class);
		assertThat(caller.calledModels).containsExactly("primary", "fallback");
	}

	@Test
	void generate_shouldMakeSingleAttemptWhenFallbackBlankOrSameAsPrimary() {
		for (String fallback : new String[] {null, "", "  ", "primary"}) {
			FakeCaller caller = new FakeCaller(model -> api(503));

			assertThatThrownBy(() -> generator(caller, fallback).generate(PROMPT))
				.isInstanceOf(ReviewSuggestionUnavailableException.class);
			assertThat(caller.calledModels).as("fallback=%s", fallback).containsExactly("primary");
		}
	}

	private static final String TRANSIENT = "후기 제안을 지금은 사용할 수 없습니다. 잠시 후 다시 시도하세요.";
	private static final String MISCONFIGURED = "AI 후기 제안 설정에 문제가 있어 사용할 수 없습니다. 관리자에게 문의하세요.";

	private String failureMessage(FakeCaller caller, String fallback) {
		return catchThrowableOfType(ReviewSuggestionUnavailableException.class,
			() -> generator(caller, fallback).generate(PROMPT)).getMessage();
	}

	@Test
	void generate_shouldReportMisconfiguredForConfigurationErrors() {
		for (int code : new int[] {400, 401, 403, 404}) {
			assertThat(failureMessage(new FakeCaller(model -> api(code)), "fallback")).isEqualTo(MISCONFIGURED);
		}
	}

	@Test
	void generate_shouldReportTransientForRetryableAndUnexpectedFailures() {
		List<Object> failures = List.of(api(429), api(503), new GenAiIOException("timeout"), "not json", "",
			new IllegalStateException("bug"));
		for (Object failure : failures) {
			assertThat(failureMessage(new FakeCaller(model -> failure), "fallback")).as("failure=%s", failure)
				.isEqualTo(TRANSIENT);
		}
	}

	@Test
	void generate_shouldFollowTheLastFailureWhenFallbackIsUsed() {
		assertThat(failureMessage(new FakeCaller(model -> "primary".equals(model) ? api(503) : api(404)), "fallback"))
			.isEqualTo(MISCONFIGURED);
		assertThat(failureMessage(new FakeCaller(model -> api(503)), "fallback")).isEqualTo(TRANSIENT);
	}

	@Test
	void generate_shouldNotExposeGeminiErrorTextInResponse() {
		ApiException e = new ApiException(400, "INVALID_ARGUMENT", "Thinking level MINIMAL is not supported");

		assertThat(failureMessage(new FakeCaller(model -> e), "fallback")).doesNotContain("MINIMAL");
	}
}
