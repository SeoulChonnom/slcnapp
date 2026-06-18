package com.seoulchonnom.spec.trip.entity.vo;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Option implements JsonSerializable {
	private String id;
	private String text;

	public Option(OptionCdo tripQuizOptionCdo, String id) {
		this.id = id;
		this.text = tripQuizOptionCdo.getText();
	}
}
