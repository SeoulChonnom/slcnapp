package com.seoulchonnom.spec.house.facade.sdo;

import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchoolRdo {
	private SchoolLevel level;
	private String name;
	/** 단지 좌표에서 학교까지 직선거리(m) */
	private int distanceM;
}
