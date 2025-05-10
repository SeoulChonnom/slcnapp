package com.seoulchonnom.slcnapp.trip.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 8, nullable = false)
    private String date;

    @Column(length = 1, nullable = false)
    private String type;

    @Column(length = 10, nullable = false)
    private String info1;

    @Column(length = 30, nullable = false)
    private String info2;

    @Column(nullable = false)
    private String logo;

    @Column(nullable = false)
    private String map1;

    @Setter
    private String map2;

    @Column(length = 30)
    private String button1;

    @Column(length = 30)
    private String button2;

    @Column(nullable = false)
    private String drive;

    @Column(length = 50, nullable = false)
    private String quizTitle;

    @Column(length = 2, nullable = false)
    private String quizAnswer;

    @Column(length = 50, nullable = false)
    private String quizAnswerTitle;

    @Column(length = 50, nullable = false)
    private String quizAnswerText;

    @Column(length = 50, nullable = false)
    private String quizErrorTitle;

    @Column(length = 50, nullable = false)
    private String quizErrorText;

    @Setter
    @Builder.Default
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL)
    private List<Quiz> quizList = new ArrayList<>();

}
