package com.seoulchonnom.slcnapp.trip.dto;

import com.seoulchonnom.slcnapp.trip.domain.Quiz;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizRegisterRequest {
    private String quizIndex;
    private String answer;

    public Quiz of() {
        return Quiz.builder()
                .quizIndex(quizIndex)
                .answer(answer)
                .build();
    }
}
