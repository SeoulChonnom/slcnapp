package com.seoulchonnom.aggregate.external.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.seoulchonnom.aggregate.inspection.exception.ReviewSuggestionUnavailableException;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestion;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionGenerator;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionPrompt;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Gemini(AI Studio) 어댑터. 응답을 JSON 스키마로 고정해 파싱 실패 여지를 줄인다.
 * 어떤 실패든(네트워크, 타임아웃, 429/5xx, 해석 불가) ReviewSuggestionUnavailableException 하나로 모아
 * 사용자는 503만 보게 한다.
 *
 * 재시도할 만한 실패(429/5xx, 네트워크·타임아웃, 응답 해석 불가)만 폴백 모델로 한 번 더 시도한다.
 * 400/401/403/404 같은 설정 오류(잘못된 키·모델 ID·미지원 thinking level)는 폴백이 가려 버리므로 바로 503이다. 로그에는 메모 내용이 섞일 수 있는 프롬프트나 응답 본문을 남기지 않는다.
 */
@Slf4j
public class GeminiReviewSuggestionGenerator implements ReviewSuggestionGenerator {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final float TEMPERATURE = 0.3f;

	/**
	 * SDK 호출 지점. 재시도/분류 로직을 네트워크 없이 시험하려고 분리했다.
	 */
	@FunctionalInterface
	interface ModelCaller {
		String call(String model, String content, GenerateContentConfig config);
	}

	/**
	 * 폴백으로 넘어갈 수 있는 실패. 최종 실패가 되면 던질 예외를 미리 만들어 들고 다닌다.
	 */
	private static class RetryableFailure extends RuntimeException {
		private final ReviewSuggestionUnavailableException result;

		RetryableFailure(Throwable cause) {
			super(cause);
			this.result = ReviewSuggestionUnavailableException.transientFailure(cause);
		}
	}

	private final ModelCaller caller;
	private final String model;
	/**
	 * null이면 폴백하지 않는다. 비어 있거나 주 모델과 같으면 꺼진 것으로 본다.
	 */
	private final String fallbackModel;
	private final int timeoutMillis;
	/**
	 * null이면 thinkingConfig를 보내지 않는다(thinkingLevel을 모르는 2.5 계열 등).
	 */
	private final ThinkingLevel.Known thinkingLevel;

	/**
	 * @param thinkingLevel MINIMAL/LOW/MEDIUM/HIGH, 비어 있으면 보내지 않는다. 잘못된 값은 요청마다 400이 되지 않도록
	 *                      여기서(빈 생성 시점에) IllegalArgumentException으로 막는다.
	 */
	public GeminiReviewSuggestionGenerator(Client client, String model, String fallbackModel, int timeoutSeconds,
		String thinkingLevel) {
		this((callModel, content, config) -> client.models.generateContent(callModel, content, config).text(), model,
			fallbackModel, timeoutSeconds, thinkingLevel);
	}

	GeminiReviewSuggestionGenerator(ModelCaller caller, String model, String fallbackModel, int timeoutSeconds,
		String thinkingLevel) {
		this.caller = caller;
		this.model = model;
		this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() || fallbackModel.trim().equals(model)
			? null : fallbackModel.trim();
		this.timeoutMillis = Math.toIntExact(timeoutSeconds * 1000L);
		this.thinkingLevel = parseThinkingLevel(thinkingLevel);
	}

	@Override
	public ReviewSuggestion generate(ReviewSuggestionPrompt prompt) {
		// 두 시도가 같은 설정(지시문, 스키마, 온도, thinking, 시도당 타임아웃)을 쓰도록 한 번만 만든다.
		GenerateContentConfig config = config(prompt);
		try {
			return attempt(model, prompt, config);
		} catch (RetryableFailure primaryFailure) {
			if (fallbackModel == null) {
				throw primaryFailure.result;
			}
			log.warn("주 모델 호출이 실패해 폴백 모델로 한 번 더 시도합니다. model={}, fallbackModel={}", model, fallbackModel);
			try {
				return attempt(fallbackModel, prompt, config);
			} catch (RetryableFailure fallbackFailure) {
				throw fallbackFailure.result;
			}
		}
	}

	/**
	 * 한 모델에 한 번 호출한다. 재시도할 만한 실패는 RetryableFailure, 그 밖의 실패는 바로 사용 불가로 던진다.
	 * 로그에는 모델과 예외 종류, 상태 코드만 남긴다 — 프롬프트와 응답 본문은 남기지 않는다.
	 */
	private ReviewSuggestion attempt(String targetModel, ReviewSuggestionPrompt prompt,
		GenerateContentConfig config) {
		String json;
		try {
			json = caller.call(targetModel, prompt.content(), config);
		} catch (ApiException e) {
			// 메시지는 Gemini의 오류 설명(설정·상태)이고 요청 본문을 되풀이하지 않는다. 응답에는 싣지 않는다.
			if (e.code() == 429 || e.code() >= 500) {
				log.warn("후기 제안 호출이 실패했습니다. model={}, status={}, message={}", targetModel, e.code(),
					e.getMessage());
				throw new RetryableFailure(e);
			}
			log.error("후기 제안 호출이 설정 오류로 거절되었습니다. model={}, status={}, message={}", targetModel, e.code(),
				e.getMessage());
			throw ReviewSuggestionUnavailableException.misconfigured(e);
		} catch (GenAiIOException e) {
			Throwable root = rootOf(e);
			log.warn("후기 제안 호출이 실패했습니다. model={}, message={}, root={}: {}", targetModel, e.getMessage(),
				root.getClass().getSimpleName(), root.getMessage());
			throw new RetryableFailure(e);
		} catch (RuntimeException e) {
			// 예상하지 못한 예외는 버그 경로라 스택까지 남긴다.
			log.error("후기 제안 호출 중 예상하지 못한 오류가 발생했습니다. model={}", targetModel, e);
			throw ReviewSuggestionUnavailableException.transientFailure(e);
		}
		try {
			return parse(json);
		} catch (ReviewSuggestionUnavailableException e) {
			log.warn("후기 제안 응답을 해석하지 못했습니다. model={}", targetModel);
			throw new RetryableFailure(e);
		}
	}

	private static Throwable rootOf(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root;
	}

	private static ThinkingLevel.Known parseThinkingLevel(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return ThinkingLevel.Known.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("slcn.ai.gemini.thinking-level 값이 올바르지 않습니다: " + value, e);
		}
	}

	GenerateContentConfig config(ReviewSuggestionPrompt prompt) {
		GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
			.systemInstruction(Content.fromParts(Part.fromText(prompt.instruction())))
			.temperature(TEMPERATURE)
			.responseMimeType("application/json")
			.responseSchema(responseSchema())
			.httpOptions(HttpOptions.builder().timeout(timeoutMillis).build());
		if (thinkingLevel != null) {
			builder.thinkingConfig(ThinkingConfig.builder().thinkingLevel(thinkingLevel).build());
		}
		return builder.build();
	}

	private Schema responseSchema() {
		Schema string = Schema.builder().type("STRING").build();
		return Schema.builder()
			.type("OBJECT")
			.properties(Map.of(
				"oneLineReview", string,
				"pros", string,
				"cons", string,
				"tags", Schema.builder().type("ARRAY").items(string).build()))
			.required(List.of("oneLineReview", "pros", "cons", "tags"))
			.build();
	}

	/**
	 * 네트워크 없이 시험할 수 있도록 분리했다. 필드가 빠지거나 타입이 달라도 빈 값으로 받고,
	 * JSON 자체가 아니면 사용 불가로 본다.
	 */
	static ReviewSuggestion parse(String json) {
		if (json == null || json.isBlank()) {
			log.warn("후기 제안 응답이 비어 있습니다.");
			throw ReviewSuggestionUnavailableException.transientFailure(null);
		}
		JsonNode root;
		try {
			root = OBJECT_MAPPER.readTree(json);
		} catch (Exception e) {
			log.warn("후기 제안 응답을 JSON으로 해석하지 못했습니다. error={}", e.getClass().getSimpleName());
			throw ReviewSuggestionUnavailableException.transientFailure(e);
		}
		if (root == null || !root.isObject()) {
			log.warn("후기 제안 응답이 JSON 객체가 아닙니다.");
			throw ReviewSuggestionUnavailableException.transientFailure(null);
		}
		List<String> tags = new ArrayList<>();
		JsonNode tagNodes = root.path("tags");
		if (tagNodes.isArray()) {
			tagNodes.forEach(node -> {
				if (node.isTextual()) {
					tags.add(node.asText());
				}
			});
		}
		return new ReviewSuggestion(textOf(root, "oneLineReview"), textOf(root, "pros"),
			textOf(root, "cons"), tags);
	}

	private static String textOf(JsonNode root, String field) {
		JsonNode node = root.path(field);
		return node.isTextual() ? node.asText() : null;
	}
}
