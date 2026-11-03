package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedevelopmentRdo {
	private String name;
	private String projectType;
	private String stage;
	private int distanceM;
	private String sourceUrl;
}
