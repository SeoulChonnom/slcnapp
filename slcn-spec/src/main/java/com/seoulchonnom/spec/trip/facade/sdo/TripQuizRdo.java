package com.seoulchonnom.spec.trip.facade.sdo;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TripQuizRdo {
	private String title;
	private List<TripQuizOptionRdo> options;
}
