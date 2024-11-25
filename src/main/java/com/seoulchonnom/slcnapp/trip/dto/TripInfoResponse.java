package com.seoulchonnom.slcnapp.trip.dto;

import com.seoulchonnom.slcnapp.trip.domain.Trip;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TripInfoResponse {
	private String date;
	private String map1;
	private String map2;
	private String button1;
	private String button2;
	private String drive;

	public static TripInfoResponse from(Trip trip) {
		return TripInfoResponse.builder()
			.date(trip.getDate())
			.map1(trip.getMap1())
			.map2(trip.getMap2())
			.button1(trip.getButton1())
			.button2(trip.getButton2())
			.drive(trip.getDrive())
			.build();
	}
}
