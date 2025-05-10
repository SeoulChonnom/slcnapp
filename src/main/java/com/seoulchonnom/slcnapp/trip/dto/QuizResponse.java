package com.seoulchonnom.slcnapp.trip.dto;

import com.seoulchonnom.slcnapp.trip.domain.Quiz;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizResponse {
    private String quizIndex;
    private String answer;

    public static QuizResponse from(Quiz quiz) {
        return QuizResponse.builder()
                .quizIndex(quiz.getQuizIndex())
                .answer(quiz.getAnswer())
                .build();
    }
}
