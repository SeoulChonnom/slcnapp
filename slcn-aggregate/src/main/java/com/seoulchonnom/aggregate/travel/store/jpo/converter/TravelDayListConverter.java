package com.seoulchonnom.aggregate.travel.store.jpo.converter;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.travel.entity.vo.TravelDay;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TravelDayListConverter implements AttributeConverter<List<TravelDay>, String> {
	@Override
	public String convertToDatabaseColumn(List<TravelDay> attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public List<TravelDay> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return List.of();
		}
		return JsonUtil.fromJson(dbData, new TypeReference<>() {
		});
	}
}
