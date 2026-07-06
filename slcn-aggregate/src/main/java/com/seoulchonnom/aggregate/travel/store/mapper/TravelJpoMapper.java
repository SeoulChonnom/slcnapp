package com.seoulchonnom.aggregate.travel.store.mapper;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelJpo;
import com.seoulchonnom.spec.travel.entity.Travel;

@Component
public class TravelJpoMapper {
	public TravelJpo toJpo(Travel travel) {
		TravelJpo jpo = new TravelJpo(
			travel.getTitle(),
			travel.getRegion(),
			travel.getStartDate(),
			travel.getEndDate(),
			travel.isHidden(),
			travel.getDays() == null ? new ArrayList<>() : new ArrayList<>(travel.getDays()),
			travel.getTags() == null ? new ArrayList<>() : new ArrayList<>(travel.getTags()),
			travel.getReview()
		);
		jpo.setId(travel.getId());
		jpo.setEntityVersion(travel.getEntityVersion());
		jpo.setRegisteredTime(travel.getRegisteredTime());
		jpo.setModifiedTime(travel.getModifiedTime());
		return jpo;
	}

	public Travel toDomain(TravelJpo jpo) {
		Travel travel = Travel.builder()
			.title(jpo.getTitle())
			.region(jpo.getRegion())
			.startDate(jpo.getStartDate())
			.endDate(jpo.getEndDate())
			.hidden(jpo.isHidden())
			.days(jpo.getDays() == null ? new ArrayList<>() : new ArrayList<>(jpo.getDays()))
			.tags(jpo.getTags() == null ? new ArrayList<>() : new ArrayList<>(jpo.getTags()))
			.review(jpo.getReview())
			.build();
		travel.setId(jpo.getId());
		travel.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			travel.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			travel.setModifiedTime(jpo.getModifiedTime());
		}
		return travel;
	}
}
