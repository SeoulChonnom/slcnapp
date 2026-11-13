package com.seoulchonnom.aggregate.house.store.jpo.converter;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PriceCapListConverter implements AttributeConverter<List<PriceCap>, String> {
	@Override
	public String convertToDatabaseColumn(List<PriceCap> attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public List<PriceCap> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return List.of();
		}
		return JsonUtil.fromJson(dbData, new TypeReference<>() {
		});
	}
}
