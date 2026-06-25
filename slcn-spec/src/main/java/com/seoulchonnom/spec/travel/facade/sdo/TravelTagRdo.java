package com.seoulchonnom.spec.travel.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelTagRdo {
	private String id;
	private String travelId;
	private String name;
	private int sortOrder;
}
