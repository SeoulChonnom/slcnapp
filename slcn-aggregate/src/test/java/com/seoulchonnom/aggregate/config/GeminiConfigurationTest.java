package com.seoulchonnom.aggregate.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.google.genai.Client;
import com.seoulchonnom.aggregate.external.gemini.GeminiReviewSuggestionGenerator;
import com.seoulchonnom.aggregate.inspection.suggestion.DisabledReviewSuggestionGenerator;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionGenerator;

class GeminiConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(GeminiConfiguration.class);

	@Test
	void blankApiKey_shouldUseDisabledGeneratorAndCreateNoClient() {
		runner.withPropertyValues("slcn.ai.gemini.api-key=").run(context -> {
			assertThat(context.getBean(ReviewSuggestionGenerator.class))
				.isInstanceOf(DisabledReviewSuggestionGenerator.class);
			assertThat(context).doesNotHaveBean(Client.class);
		});
	}

	@Test
	void missingApiKey_shouldUseDisabledGenerator() {
		runner.run(context -> assertThat(context.getBean(ReviewSuggestionGenerator.class))
			.isInstanceOf(DisabledReviewSuggestionGenerator.class));
	}

	@Test
	void presentApiKey_shouldUseGeminiGeneratorWithClient() {
		runner.withPropertyValues("slcn.ai.gemini.api-key=test-key").run(context -> {
			assertThat(context.getBean(ReviewSuggestionGenerator.class))
				.isInstanceOf(GeminiReviewSuggestionGenerator.class);
			assertThat(context).hasSingleBean(Client.class);
		});
	}

	@Test
	void fallbackModelProperty_shouldBeAcceptedBlankOrSet() {
		runner.withPropertyValues("slcn.ai.gemini.api-key=test-key", "slcn.ai.gemini.fallback-model=")
			.run(context -> assertThat(context).hasNotFailed());
		runner.withPropertyValues("slcn.ai.gemini.api-key=test-key", "slcn.ai.gemini.fallback-model=other-model")
			.run(context -> assertThat(context.getBean(ReviewSuggestionGenerator.class))
				.isInstanceOf(GeminiReviewSuggestionGenerator.class));
	}

	@Test
	void invalidThinkingLevel_shouldFailAtStartup() {
		runner.withPropertyValues("slcn.ai.gemini.api-key=test-key", "slcn.ai.gemini.thinking-level=TURBO")
			.run(context -> assertThat(context).hasFailed());
	}
}
