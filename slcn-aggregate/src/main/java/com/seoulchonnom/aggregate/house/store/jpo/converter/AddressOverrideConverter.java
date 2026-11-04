package com.seoulchonnom.aggregate.house.store.jpo.converter;

import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AddressOverrideConverter implements AttributeConverter<AddressOverride, String> {
	@Override
	public String convertToDatabaseColumn(AddressOverride attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public AddressOverride convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		return JsonUtil.fromJson(dbData, AddressOverride.class);
	}
}
