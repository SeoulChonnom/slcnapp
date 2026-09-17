package com.seoulchonnom.aggregate.inspection.store.jpo.converter;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 질문 버전 이력을 질문 행 안에 JSON으로 싣는다. 중첩된 QuestionChoice도 함께 처리된다.
 */
@Converter
public class QuestionVersionListConverter implements AttributeConverter<List<QuestionVersion>, String> {
	@Override
	public String convertToDatabaseColumn(List<QuestionVersion> attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public List<QuestionVersion> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return List.of();
		}
		return JsonUtil.fromJson(dbData, new TypeReference<>() {
		});
	}
}
