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
public class TravelDayRdo {
	private String date;
	private String title;
	private String memo;
	private int dayNumber;
	private int sortOrder;
	private FileBoxItemRdo cover;
	private List<FileBoxItemRdo> photos = new ArrayList<>();
	private List<TravelPlaceRdo> places = new ArrayList<>();
}
