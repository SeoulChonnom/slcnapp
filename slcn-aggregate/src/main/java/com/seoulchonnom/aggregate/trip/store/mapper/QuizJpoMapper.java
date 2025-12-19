package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;

import org.mapstruct.Mapper;

import com.seoulchonnom.aggregate.trip.store.jpo.QuizJpo;
import com.seoulchonnom.spec.trip.entity.Quiz;

@Mapper(componentModel = SPRING)
public interface QuizJpoMapper {
	Quiz toDomain(QuizJpo quizJpo);

	List<Quiz> toDomainList(List<QuizJpo> quizJpoList);

	QuizJpo toJpo(Quiz quiz);

	List<QuizJpo> toJpoList(List<Quiz> quizList);
}
