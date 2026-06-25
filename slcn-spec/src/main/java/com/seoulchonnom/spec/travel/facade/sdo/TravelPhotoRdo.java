package com.seoulchonnom.spec.travel.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelPhotoRdo {
	private String id;
	private String travelId;
	private String travelDayId;
	private String travelPlaceId;
	private String photoFileId;
	private String caption;
	private int sortOrder;
}
