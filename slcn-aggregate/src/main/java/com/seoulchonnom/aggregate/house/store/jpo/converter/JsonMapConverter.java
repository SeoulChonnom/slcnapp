package com.seoulchonnom.aggregate.house.store.jpo.converter;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.seoulchonnom.spec.common.util.JsonUtil;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 배치 기록의 detail처럼 모양이 단계마다 다른 JSON. Map은 자체 equals가 있어 더티 체크가 안정적이다.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
	private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Map<String, Object> attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public Map<String, Object> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		return JsonUtil.fromJson(dbData, TYPE);
	}
}
