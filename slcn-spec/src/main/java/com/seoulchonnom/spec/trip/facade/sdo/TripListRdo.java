package com.seoulchonnom.spec.trip.facade.sdo;

import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.trip.entity.vo.TripType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripListRdo {
	private String id;
	private String date;
	private TripType type;
	private String name;
	private FileAssetRdo logo;
}
