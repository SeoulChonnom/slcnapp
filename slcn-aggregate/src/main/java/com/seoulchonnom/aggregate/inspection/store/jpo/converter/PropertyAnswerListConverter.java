package com.seoulchonnom.aggregate.inspection.store.jpo.converter;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 문답 스냅샷과 값을 매물 행 안에 JSON으로 싣는다. 중첩된 QuestionChoice도 함께 처리된다.
 */
@Converter
public class PropertyAnswerListConverter implements AttributeConverter<List<PropertyAnswer>, String> {
	@Override
	public String convertToDatabaseColumn(List<PropertyAnswer> attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public List<PropertyAnswer> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return List.of();
		}
		return JsonUtil.fromJson(dbData, new TypeReference<>() {
		});
	}
}
