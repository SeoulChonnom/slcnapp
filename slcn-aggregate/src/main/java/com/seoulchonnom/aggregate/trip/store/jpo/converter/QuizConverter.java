package com.seoulchonnom.aggregate.trip.store.jpo.converter;

import com.seoulchonnom.spec.common.util.JsonUtil;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class QuizConverter implements AttributeConverter<Quiz, String> {

	@Override
	public String convertToDatabaseColumn(Quiz attribute) {
		return attribute == null ? null : attribute.toJson();
	}

	@Override
	public Quiz convertToEntityAttribute(String dbData) {
		return dbData == null ? null : JsonUtil.fromJson(dbData, Quiz.class);
	}
}
