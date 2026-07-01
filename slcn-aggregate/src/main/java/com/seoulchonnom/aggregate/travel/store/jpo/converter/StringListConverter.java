package com.seoulchonnom.aggregate.travel.store.jpo.converter;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.seoulchonnom.spec.common.util.JsonUtil;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
	@Override
	public String convertToDatabaseColumn(List<String> attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public List<String> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return List.of();
		}
		return JsonUtil.fromJson(dbData, new TypeReference<>() {
		});
	}
}
