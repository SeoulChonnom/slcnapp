package com.seoulchonnom.spec.trip.entity;

import java.util.UUID;

import com.seoulchonnom.spec.trip.facade.sdo.TripQuizOptionCdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class TripQuizOption {
	private String id;
	private String tripId;
	private String text;
	private int sortOrder;

	public TripQuizOption(TripQuizOptionCdo tripQuizOptionCdo, String tripId) {
		this.id = UUID.randomUUID().toString();
		this.tripId = tripId;
		this.text = tripQuizOptionCdo.getText();
		this.sortOrder = tripQuizOptionCdo.getSortOrder();
	}
}
