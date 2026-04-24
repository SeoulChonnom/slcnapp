package com.seoulchonnom.spec.trip.entity.vo;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class Option implements JsonSerializable {
	private String id;
	private String text;
	private int sortOrder;

	public Option(OptionCdo tripQuizOptionCdo, String id) {
		this.id = id;
		this.text = tripQuizOptionCdo.getText();
		this.sortOrder = tripQuizOptionCdo.getSortOrder();
	}
}
