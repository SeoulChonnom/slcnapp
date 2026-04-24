package com.seoulchonnom.spec.common.util;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonUtil {

	private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

	private JsonUtil() {
		throw new UnsupportedOperationException("Utility class");
	}

	private static ObjectMapper createObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();

		// Java 8+ Date/Time(LocalDateTime 등) 지원
		objectMapper.registerModule(new JavaTimeModule());

		// 날짜를 timestamp 숫자가 아니라 문자열로 직렬화
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		// 알 수 없는 필드가 들어와도 역직렬화 실패하지 않도록
		objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

		return objectMapper;
	}

	public static String toJson(Object obj) {
		try {
			return OBJECT_MAPPER.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to serialize object to JSON.", e);
		}
	}

	public static String toPrettyJson(Object obj) {
		try {
			return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to serialize object to pretty JSON.", e);
		}
	}

	public static <T> T fromJson(String json, Class<T> clazz) {
		try {
			return OBJECT_MAPPER.readValue(json, clazz);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to deserialize JSON to " + clazz.getName(), e);
		}
	}

	public static <T> T fromJson(String json, TypeReference<T> typeReference) {
		try {
			return OBJECT_MAPPER.readValue(json, typeReference);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to deserialize JSON with TypeReference.", e);
		}
	}

	public static <T> List<T> fromJsonList(String json, Class<T> elementType) {
		if (json == null || json.isBlank()) {
			return Collections.emptyList();
		}

		try {
			JavaType javaType = OBJECT_MAPPER.getTypeFactory()
				.constructCollectionType(List.class, elementType);

			List<T> result = OBJECT_MAPPER.readValue(json, javaType);
			return result != null ? result : Collections.emptyList();
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException(
				"Failed to deserialize JSON to List<" + elementType.getName() + ">",
				e
			);
		}
	}

	public static ObjectMapper getObjectMapper() {
		return OBJECT_MAPPER;
	}
}