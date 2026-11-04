package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexSearchItemRdo {
	private String complexId;
	private String name;
	private String districtName;
	private String umdName;
	private Integer buildYear;
}
