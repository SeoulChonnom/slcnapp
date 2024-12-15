package com.seoulchonnom.slcnapp.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripRegisterRequest {
    private String date;
    private String type;
    private String info1;
    private String info2;
    private String button1;
    private String button2;
    private String drive;
    private String quizTitle;
    private String quizAnswer;
    private String quizAnswerTitle;
    private String quizAnswerText;
    private String quizErrorTitle;
    private String quizErrorText;
    private List<QuizRegisterRequest> quizRegisterRequestList;
}
