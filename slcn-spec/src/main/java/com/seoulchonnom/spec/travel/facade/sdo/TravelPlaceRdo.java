package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelPlaceRdo {
	private String placeKey;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private int sortOrder;
	private FileBoxItemRdo cover;
	private List<FileBoxItemRdo> photos = new ArrayList<>();
}
