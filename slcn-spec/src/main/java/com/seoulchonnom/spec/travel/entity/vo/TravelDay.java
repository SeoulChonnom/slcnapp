package com.seoulchonnom.spec.travel.entity.vo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class TravelDay implements JsonSerializable {
	private LocalDate date;
	private String title;
	private String memo;
	private int dayNumber;
	private int sortOrder;
	@Builder.Default
	private List<TravelPlace> places = new ArrayList<>();
}
