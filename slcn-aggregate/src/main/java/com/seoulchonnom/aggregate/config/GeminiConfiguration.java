package com.seoulchonnom.aggregate.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.seoulchonnom.aggregate.external.gemini.GeminiReviewSuggestionGenerator;
import com.seoulchonnom.aggregate.inspection.suggestion.DisabledReviewSuggestionGenerator;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionGenerator;

/**
 * 후기 제안 생성기 선택. API 키가 없으면 대역을 써서 개발 환경과 테스트가 키 없이 그대로 돈다.
 * SDK 클라이언트는 키가 있을 때만 만든다.
 */
@Configuration
public class GeminiConfiguration {
	private static final String KEY_PRESENT = "!'${slcn.ai.gemini.api-key:}'.trim().isEmpty()";

	@Bean
	public ReviewSuggestionGenerator reviewSuggestionGenerator(
		@Value("${slcn.ai.gemini.api-key:}") String apiKey,
		@Value("${slcn.ai.gemini.model:gemini-3.5-flash-lite}") String model,
		@Value("${slcn.ai.gemini.fallback-model:gemini-3.1-flash-lite}") String fallbackModel,
		@Value("${slcn.ai.gemini.timeout-seconds:15}") int timeoutSeconds,
		@Value("${slcn.ai.gemini.thinking-level:LOW}") String thinkingLevel,
		ObjectProvider<Client> client) {
		if (apiKey == null || apiKey.isBlank()) {
			return new DisabledReviewSuggestionGenerator();
		}
		return new GeminiReviewSuggestionGenerator(client.getObject(), model, fallbackModel, timeoutSeconds,
			thinkingLevel);
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnExpression(KEY_PRESENT)
	public Client geminiClient(@Value("${slcn.ai.gemini.api-key}") String apiKey) {
		return Client.builder().apiKey(apiKey.trim()).build();
	}
}
