package com.seoulchonnom.spec.travel.mapper;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.TravelDay;
import com.seoulchonnom.spec.travel.entity.TravelPhoto;
import com.seoulchonnom.spec.travel.entity.TravelPlace;
import com.seoulchonnom.spec.travel.entity.TravelReview;
import com.seoulchonnom.spec.travel.entity.TravelTag;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPhotoRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelTagRdo;

@Component
public class TravelMapper {
	public TravelRdo toTravelRdo(Travel travel) {
		TravelRdo travelRdo = new TravelRdo();
		travelRdo.setId(travel.getId());
		travelRdo.setTravelId(travel.getId());
		travelRdo.setTitle(travel.getTitle());
		travelRdo.setRegion(travel.getRegion());
		travelRdo.setStartDate(travel.getStartDate().toString());
		travelRdo.setEndDate(travel.getEndDate().toString());
		travelRdo.setCoverPhotoId(travel.getCoverPhotoId());
		travelRdo.setOneLineReview(travel.getOneLineReview());
		travelRdo.setNights((int)ChronoUnit.DAYS.between(travel.getStartDate(), travel.getEndDate()));
		travelRdo.setDays(travelRdo.getNights() + 1);
		return travelRdo;
	}

	public TravelRdo toTravelRdo(Travel travel, List<TravelTag> tags, TravelReview review) {
		TravelRdo travelRdo = toTravelRdo(travel);
		travelRdo.setTags(tags.stream().map(this::toTravelTagRdo).toList());
		if (review != null && review.getOneLineSummary() != null) {
			travelRdo.setOneLineReview(review.getOneLineSummary());
		}
		return travelRdo;
	}

	public TravelDetailRdo toTravelDetailRdo(Travel travel, List<TravelDay> days, List<TravelPlace> places,
		List<TravelPhoto> photos, List<TravelTag> tags, TravelReview review) {
		TravelRdo base = toTravelRdo(travel);
		Map<String, List<TravelPlaceRdo>> placesByDayId = places.stream()
			.map(this::toTravelPlaceRdo)
			.collect(Collectors.groupingBy(TravelPlaceRdo::getTravelDayId));
		Map<String, List<TravelPhotoRdo>> photosByDayId = photos.stream()
			.filter(photo -> photo.getTravelDayId() != null)
			.map(this::toTravelPhotoRdo)
			.collect(Collectors.groupingBy(TravelPhotoRdo::getTravelDayId));
		Map<String, List<TravelPhotoRdo>> photosByPlaceId = photos.stream()
			.filter(photo -> photo.getTravelPlaceId() != null)
			.map(this::toTravelPhotoRdo)
			.collect(Collectors.groupingBy(TravelPhotoRdo::getTravelPlaceId));
		TravelDetailRdo detailRdo = new TravelDetailRdo();
		detailRdo.setId(base.getId());
		detailRdo.setTravelId(base.getTravelId());
		detailRdo.setTitle(base.getTitle());
		detailRdo.setRegion(base.getRegion());
		detailRdo.setStartDate(base.getStartDate());
		detailRdo.setEndDate(base.getEndDate());
		detailRdo.setCoverPhotoId(base.getCoverPhotoId());
		detailRdo.setOneLineReview(base.getOneLineReview());
		detailRdo.setNights(base.getNights());
		detailRdo.setDays(base.getDays());
		detailRdo.setTravelDays(days.stream()
			.map(day -> toTravelDayRdo(day, placesByDayId.getOrDefault(day.getId(), List.of()),
				photosByDayId.getOrDefault(day.getId(), List.of())))
			.toList());
		detailRdo.setPlaces(places.stream()
			.map(this::toTravelPlaceRdo)
			.peek(place -> place.setPhotos(photosByPlaceId.getOrDefault(place.getId(), List.of())))
			.toList());
		detailRdo.setPhotos(photos.stream().map(this::toTravelPhotoRdo).toList());
		detailRdo.setTags(tags.stream().map(this::toTravelTagRdo).toList());
		detailRdo.setReview(review == null ? null : toTravelReviewRdo(review));
		if (review != null && review.getOneLineSummary() != null) {
			detailRdo.setOneLineReview(review.getOneLineSummary());
		}
		return detailRdo;
	}

	public TravelDayRdo toTravelDayRdo(TravelDay travelDay) {
		TravelDayRdo rdo = new TravelDayRdo();
		rdo.setId(travelDay.getId());
		rdo.setTravelId(travelDay.getTravelId());
		rdo.setDate(travelDay.getDate().toString());
		rdo.setTitle(travelDay.getTitle());
		rdo.setMemo(travelDay.getMemo());
		rdo.setCoverPhotoId(travelDay.getCoverPhotoId());
		rdo.setDayNumber(travelDay.getDayNumber());
		rdo.setSortOrder(travelDay.getSortOrder());
		return rdo;
	}

	public TravelDayRdo toTravelDayRdo(TravelDay travelDay, List<TravelPlaceRdo> places, List<TravelPhotoRdo> photos) {
		TravelDayRdo rdo = toTravelDayRdo(travelDay);
		rdo.setPlaces(places);
		rdo.setPhotos(photos);
		return rdo;
	}

	public TravelPlaceRdo toTravelPlaceRdo(TravelPlace travelPlace) {
		TravelPlaceRdo rdo = new TravelPlaceRdo();
		rdo.setId(travelPlace.getId());
		rdo.setTravelId(travelPlace.getTravelId());
		rdo.setTravelDayId(travelPlace.getTravelDayId());
		rdo.setName(travelPlace.getName());
		rdo.setCategory(travelPlace.getCategory());
		rdo.setAddress(travelPlace.getAddress());
		rdo.setMemo(travelPlace.getMemo());
		rdo.setDescription(travelPlace.getDescription() != null ? travelPlace.getDescription() : travelPlace.getMemo());
		rdo.setCoverPhotoId(travelPlace.getCoverPhotoId());
		rdo.setSortOrder(travelPlace.getSortOrder());
		return rdo;
	}

	public TravelPhotoRdo toTravelPhotoRdo(TravelPhoto travelPhoto) {
		TravelPhotoRdo rdo = new TravelPhotoRdo();
		rdo.setId(travelPhoto.getId());
		rdo.setTravelId(travelPhoto.getTravelId());
		rdo.setTravelDayId(travelPhoto.getTravelDayId());
		rdo.setTravelPlaceId(travelPhoto.getTravelPlaceId());
		rdo.setPhotoFileId(travelPhoto.getPhotoFileId());
		rdo.setCaption(travelPhoto.getCaption());
		rdo.setSortOrder(travelPhoto.getSortOrder());
		return rdo;
	}

	public TravelTagRdo toTravelTagRdo(TravelTag travelTag) {
		TravelTagRdo rdo = new TravelTagRdo();
		rdo.setId(travelTag.getId());
		rdo.setTravelId(travelTag.getTravelId());
		rdo.setName(travelTag.getName());
		rdo.setSortOrder(travelTag.getSortOrder());
		return rdo;
	}

	public TravelReviewRdo toTravelReviewRdo(TravelReview travelReview) {
		TravelReviewRdo rdo = new TravelReviewRdo();
		rdo.setId(travelReview.getId());
		rdo.setTravelId(travelReview.getTravelId());
		rdo.setContent(travelReview.getContent());
		rdo.setOneLineSummary(travelReview.getOneLineSummary());
		rdo.setGoodPoint(travelReview.getGoodPoint());
		rdo.setBadPoint(travelReview.getBadPoint());
		rdo.setRevisitPlace(travelReview.getRevisitPlace());
		rdo.setFinalReview(travelReview.getFinalReview());
		return rdo;
	}
}
