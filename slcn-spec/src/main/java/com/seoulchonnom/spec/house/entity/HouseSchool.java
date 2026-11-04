package com.seoulchonnom.spec.house.entity;

import java.time.LocalDate;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 학교 위치 참조 데이터. 학교 표준데이터에서 180일마다 받아 school_code 기준으로 교체한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseSchool extends DomainEntity {
	/** 표준데이터 학교 ID(schoolId) */
	private String schoolCode;
	private String name;
	private SchoolLevel level;
	private double latitude;
	private double longitude;
	private LocalDate syncedDate;
}
