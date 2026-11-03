package com.seoulchonnom.aggregate.house.store.jpo.converter;

import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.house.entity.vo.CostRates;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CostRatesConverter implements AttributeConverter<CostRates, String> {
	@Override
	public String convertToDatabaseColumn(CostRates attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public CostRates convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		return JsonUtil.fromJson(dbData, CostRates.class);
	}
}
