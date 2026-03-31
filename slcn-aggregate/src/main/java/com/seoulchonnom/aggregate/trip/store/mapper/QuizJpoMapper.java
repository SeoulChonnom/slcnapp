package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.trip.store.jpo.QuizJpo;
import com.seoulchonnom.spec.trip.entity.Quiz;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface QuizJpoMapper {
	Quiz toDomain(QuizJpo quizJpo);

	List<Quiz> toDomainList(List<QuizJpo> quizJpoList);

	QuizJpo toJpo(Quiz quiz);

	List<QuizJpo> toJpoList(List<Quiz> quizList);

	@AfterMapping
	default void mapInheritedFields(QuizJpo quizJpo, @MappingTarget Quiz quiz) {
		quiz.setId(quizJpo.getId());
		quiz.setEntityVersion(quizJpo.getEntityVersion());
		if (quizJpo.getRegisteredTime() != null) {
			quiz.setRegisteredTime(quizJpo.getRegisteredTime());
		}
		if (quizJpo.getModifiedTime() != null) {
			quiz.setModifiedTime(quizJpo.getModifiedTime());
		}
	}
}
