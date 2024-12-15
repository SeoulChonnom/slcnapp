package com.seoulchonnom.slcnapp.trip.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizRegisterRequest {
    private String quizIndex;
    private String answer;
}
