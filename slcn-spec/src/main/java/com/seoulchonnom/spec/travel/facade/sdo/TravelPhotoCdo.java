package com.seoulchonnom.spec.travel.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelPhotoCdo {
	private String travelDayId;
	private String travelPlaceId;
	private String photoFileId;
	private String caption;
	private Integer sortOrder;
}
