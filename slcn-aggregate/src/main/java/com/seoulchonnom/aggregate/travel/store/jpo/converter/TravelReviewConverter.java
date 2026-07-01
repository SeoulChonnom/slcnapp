package com.seoulchonnom.aggregate.travel.store.jpo.converter;

import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.travel.entity.vo.TravelReview;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TravelReviewConverter implements AttributeConverter<TravelReview, String> {
	@Override
	public String convertToDatabaseColumn(TravelReview attribute) {
		return attribute == null ? null : JsonUtil.toJson(attribute);
	}

	@Override
	public TravelReview convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		return JsonUtil.fromJson(dbData, TravelReview.class);
	}
}
