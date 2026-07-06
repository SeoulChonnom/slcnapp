package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelRdo {
	private String id;
	private String travelId;
	private String title;
	private String region;
	private String startDate;
	private String endDate;
	private FileBoxItemRdo cover;
	private String oneLineReview;
	private int nights;
	private int days;
	private List<String> tags = new ArrayList<>();
}
