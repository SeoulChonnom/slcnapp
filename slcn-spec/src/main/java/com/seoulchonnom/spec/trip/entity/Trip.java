package com.seoulchonnom.spec.trip.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Trip extends DomainEntity {
	private String date;
	private String type;
	private String name;
	private FileReference logo;
	private FileReference firstMap;
	private FileReference secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	private Quiz quiz;

}
