package com.seoulchonnom.slcnapp.trip;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TripConstant {
	public static final String TRIP_NOT_FOUND_ERROR_MESSAGE = "해당 일자 나들이가 없습니다.";

	public static final String RETRIEVE_TRIP_LIST_SUCCESS_MESSAGE = "나들이 조회에 성공하였습니다.";
}
