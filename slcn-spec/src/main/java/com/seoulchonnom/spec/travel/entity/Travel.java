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
public class Travel extends DomainEntity {
	private String title;
	private String region;
	private LocalDate startDate;
	private LocalDate endDate;
	private String coverPhotoId;
	private String oneLineReview;
	private boolean hidden;

	public Travel(String title, String region, LocalDate startDate, LocalDate endDate, String coverPhotoId) {
		super();
		this.title = title;
		this.region = region;
		this.startDate = startDate;
		this.endDate = endDate;
		this.coverPhotoId = coverPhotoId;
		this.hidden = false;
	}

	public void update(String title, String region, LocalDate startDate, LocalDate endDate, String coverPhotoId) {
		this.title = title;
		this.region = region;
		this.startDate = startDate;
		this.endDate = endDate;
		this.coverPhotoId = coverPhotoId;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void hide() {
		this.hidden = true;
		this.modifiedTime = System.currentTimeMillis();
	}
}
