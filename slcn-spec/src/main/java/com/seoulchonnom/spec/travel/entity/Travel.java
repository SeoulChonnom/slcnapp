package com.seoulchonnom.spec.travel.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.travel.entity.vo.TravelDay;
import com.seoulchonnom.spec.travel.entity.vo.TravelReview;

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
	private boolean hidden;
	@Builder.Default
	private List<TravelDay> days = new ArrayList<>();
	@Builder.Default
	private List<String> tags = new ArrayList<>();
	private TravelReview review;

	public Travel(String title, String region, LocalDate startDate, LocalDate endDate) {
		super();
		this.title = title;
		this.region = region;
		this.startDate = startDate;
		this.endDate = endDate;
		this.hidden = false;
	}

	public void update(String title, String region, LocalDate startDate, LocalDate endDate) {
		this.title = title;
		this.region = region;
		this.startDate = startDate;
		this.endDate = endDate;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void hide() {
		this.hidden = true;
		this.modifiedTime = System.currentTimeMillis();
	}
}
