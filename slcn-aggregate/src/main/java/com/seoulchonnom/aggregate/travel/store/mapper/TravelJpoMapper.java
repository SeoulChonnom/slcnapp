package com.seoulchonnom.aggregate.travel.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelDayJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelPhotoJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelPlaceJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelReviewJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelTagJpo;
import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.TravelDay;
import com.seoulchonnom.spec.travel.entity.TravelPhoto;
import com.seoulchonnom.spec.travel.entity.TravelPlace;
import com.seoulchonnom.spec.travel.entity.TravelReview;
import com.seoulchonnom.spec.travel.entity.TravelTag;

@Component
public class TravelJpoMapper {
	public TravelJpo toJpo(Travel travel) {
		TravelJpo jpo = new TravelJpo(travel.getTitle(), travel.getRegion(), travel.getStartDate(),
			travel.getEndDate(), travel.getCoverPhotoId(), travel.getOneLineReview(), travel.isHidden());
		copyToJpo(travel, jpo);
		return jpo;
	}

	public Travel toDomain(TravelJpo jpo) {
		Travel travel = Travel.builder()
			.title(jpo.getTitle())
			.region(jpo.getRegion())
			.startDate(jpo.getStartDate())
			.endDate(jpo.getEndDate())
			.coverPhotoId(jpo.getCoverPhotoId())
			.oneLineReview(jpo.getOneLineReview())
			.hidden(jpo.isHidden())
			.build();
		copyToDomain(jpo, travel);
		return travel;
	}

	public TravelDayJpo toJpo(TravelDay travelDay) {
		TravelDayJpo jpo = new TravelDayJpo(travelDay.getTravelId(), travelDay.getDate(), travelDay.getTitle(),
			travelDay.getMemo(), travelDay.getCoverPhotoId(), travelDay.getDayNumber(), travelDay.getSortOrder());
		copyToJpo(travelDay, jpo);
		return jpo;
	}

	public TravelDay toDomain(TravelDayJpo jpo) {
		TravelDay travelDay = TravelDay.builder()
			.travelId(jpo.getTravelId())
			.date(jpo.getDate())
			.title(jpo.getTitle())
			.memo(jpo.getMemo())
			.coverPhotoId(jpo.getCoverPhotoId())
			.dayNumber(jpo.getDayNumber())
			.sortOrder(jpo.getSortOrder())
			.build();
		copyToDomain(jpo, travelDay);
		return travelDay;
	}

	public TravelPlaceJpo toJpo(TravelPlace travelPlace) {
		TravelPlaceJpo jpo = new TravelPlaceJpo(travelPlace.getTravelId(), travelPlace.getTravelDayId(),
			travelPlace.getName(), travelPlace.getCategory(), travelPlace.getAddress(), travelPlace.getMemo(),
			travelPlace.getDescription(), travelPlace.getCoverPhotoId(), travelPlace.getSortOrder());
		copyToJpo(travelPlace, jpo);
		return jpo;
	}

	public TravelPlace toDomain(TravelPlaceJpo jpo) {
		TravelPlace travelPlace = TravelPlace.builder()
			.travelId(jpo.getTravelId())
			.travelDayId(jpo.getTravelDayId())
			.name(jpo.getName())
			.category(jpo.getCategory())
			.address(jpo.getAddress())
			.memo(jpo.getMemo())
			.description(jpo.getDescription())
			.coverPhotoId(jpo.getCoverPhotoId())
			.sortOrder(jpo.getSortOrder())
			.build();
		copyToDomain(jpo, travelPlace);
		return travelPlace;
	}

	public TravelPhotoJpo toJpo(TravelPhoto travelPhoto) {
		TravelPhotoJpo jpo = new TravelPhotoJpo(travelPhoto.getTravelId(), travelPhoto.getTravelDayId(),
			travelPhoto.getTravelPlaceId(), travelPhoto.getPhotoFileId(), travelPhoto.getCaption(),
			travelPhoto.getSortOrder());
		copyToJpo(travelPhoto, jpo);
		return jpo;
	}

	public TravelPhoto toDomain(TravelPhotoJpo jpo) {
		TravelPhoto travelPhoto = TravelPhoto.builder()
			.travelId(jpo.getTravelId())
			.travelDayId(jpo.getTravelDayId())
			.travelPlaceId(jpo.getTravelPlaceId())
			.photoFileId(jpo.getPhotoFileId())
			.caption(jpo.getCaption())
			.sortOrder(jpo.getSortOrder())
			.build();
		copyToDomain(jpo, travelPhoto);
		return travelPhoto;
	}

	public TravelTagJpo toJpo(TravelTag travelTag) {
		TravelTagJpo jpo = new TravelTagJpo(travelTag.getTravelId(), travelTag.getName(), travelTag.getSortOrder());
		copyToJpo(travelTag, jpo);
		return jpo;
	}

	public TravelTag toDomain(TravelTagJpo jpo) {
		TravelTag travelTag = TravelTag.builder()
			.travelId(jpo.getTravelId())
			.name(jpo.getName())
			.sortOrder(jpo.getSortOrder())
			.build();
		copyToDomain(jpo, travelTag);
		return travelTag;
	}

	public TravelReviewJpo toJpo(TravelReview travelReview) {
		TravelReviewJpo jpo = new TravelReviewJpo(travelReview.getTravelId(), travelReview.getContent(),
			travelReview.getOneLineSummary(), travelReview.getGoodPoint(), travelReview.getBadPoint(),
			travelReview.getRevisitPlace(), travelReview.getFinalReview());
		copyToJpo(travelReview, jpo);
		return jpo;
	}

	public TravelReview toDomain(TravelReviewJpo jpo) {
		TravelReview travelReview = TravelReview.builder()
			.travelId(jpo.getTravelId())
			.content(jpo.getContent())
			.oneLineSummary(jpo.getOneLineSummary())
			.goodPoint(jpo.getGoodPoint())
			.badPoint(jpo.getBadPoint())
			.revisitPlace(jpo.getRevisitPlace())
			.finalReview(jpo.getFinalReview())
			.build();
		copyToDomain(jpo, travelReview);
		return travelReview;
	}

	private void copyToJpo(DomainEntity domain, DomainEntityJpo jpo) {
		jpo.setId(domain.getId());
		jpo.setEntityVersion(domain.getEntityVersion());
		jpo.setRegisteredTime(domain.getRegisteredTime());
		jpo.setModifiedTime(domain.getModifiedTime());
	}

	private void copyToDomain(DomainEntityJpo jpo, DomainEntity domain) {
		domain.setId(jpo.getId());
		domain.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			domain.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			domain.setModifiedTime(jpo.getModifiedTime());
		}
	}
}
