package com.seoulchonnom.spec.travel.entity;

import java.time.LocalDate;

import com.seoulchonnom.spec.common.entity.DomainEntity;

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
public class TravelDay extends DomainEntity {
	private String travelId;
	private LocalDate date;
	private String title;
	private String memo;
	private String coverPhotoId;
	private int dayNumber;
	private int sortOrder;

	public TravelDay(String travelId, LocalDate date, int dayNumber) {
		super();
		this.travelId = travelId;
		this.date = date;
		this.dayNumber = dayNumber;
		this.sortOrder = dayNumber;
	}

	public void reorder(int dayNumber) {
		this.dayNumber = dayNumber;
		this.sortOrder = dayNumber;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void update(String title, String memo, String coverPhotoId, int sortOrder) {
		this.title = title;
		this.memo = memo;
		this.coverPhotoId = coverPhotoId;
		this.sortOrder = sortOrder;
		this.modifiedTime = System.currentTimeMillis();
	}

	public boolean hasContent() {
		return title != null || memo != null || coverPhotoId != null;
	}
}
