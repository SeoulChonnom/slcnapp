package com.seoulchonnom.spec.travel.mapper;

import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.vo.TravelDay;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlace;
import com.seoulchonnom.spec.travel.entity.vo.TravelReview;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewRdo;

@Component
public class TravelMapper {
	public TravelRdo toTravelRdo(Travel travel) {
		return toTravelRdo(travel, null);
	}

	public TravelRdo toTravelRdo(Travel travel, FileBoxItemRdo cover) {
		TravelRdo travelRdo = new TravelRdo();
		travelRdo.setId(travel.getId());
		travelRdo.setTravelId(travel.getId());
		travelRdo.setTitle(travel.getTitle());
		travelRdo.setRegion(travel.getRegion());
		travelRdo.setStartDate(travel.getStartDate().toString());
		travelRdo.setEndDate(travel.getEndDate().toString());
		travelRdo.setCover(cover);
		travelRdo.setOneLineReview(oneLineReviewOf(travel.getReview()));
		travelRdo.setNights((int)ChronoUnit.DAYS.between(travel.getStartDate(), travel.getEndDate()));
		travelRdo.setDays(travelRdo.getNights() + 1);
		travelRdo.setTags(travel.getTags());
		return travelRdo;
	}

	public TravelDetailRdo toTravelDetailRdo(Travel travel, List<FileBoxItemRdo> files) {
		List<FileBoxItemRdo> fileItems = files == null ? List.of() : files;
		TravelRdo base = toTravelRdo(travel, coverOf(fileItems, FileBoxTargetType.TRAVEL, null));

		TravelDetailRdo detailRdo = new TravelDetailRdo();
		detailRdo.setId(base.getId());
		detailRdo.setTravelId(base.getTravelId());
		detailRdo.setTitle(base.getTitle());
		detailRdo.setRegion(base.getRegion());
		detailRdo.setStartDate(base.getStartDate());
		detailRdo.setEndDate(base.getEndDate());
		detailRdo.setCover(base.getCover());
		detailRdo.setOneLineReview(base.getOneLineReview());
		detailRdo.setNights(base.getNights());
		detailRdo.setDays(base.getDays());
		detailRdo.setTravelDays(travel.getDays().stream()
			.map(day -> toTravelDayRdo(day, fileItems))
			.toList());
		detailRdo.setTags(base.getTags());
		detailRdo.setReview(travel.getReview() == null ? null : toTravelReviewRdo(travel.getReview()));
		detailRdo.setFiles(fileItems);
		return detailRdo;
	}

	public TravelDayRdo toTravelDayRdo(TravelDay travelDay, List<FileBoxItemRdo> files) {
		String targetId = travelDay.getDate().toString();
		TravelDayRdo rdo = new TravelDayRdo();
		rdo.setDate(targetId);
		rdo.setTitle(travelDay.getTitle());
		rdo.setMemo(travelDay.getMemo());
		rdo.setDayNumber(travelDay.getDayNumber());
		rdo.setSortOrder(travelDay.getSortOrder());
		rdo.setCover(coverOf(files, FileBoxTargetType.TRAVEL_DAY, targetId));
		rdo.setPhotos(photosOf(files, FileBoxTargetType.TRAVEL_DAY, targetId));
		rdo.setPlaces(travelDay.getPlaces().stream()
			.map(place -> toTravelPlaceRdo(place, files))
			.toList());
		return rdo;
	}

	public TravelPlaceRdo toTravelPlaceRdo(TravelPlace travelPlace, List<FileBoxItemRdo> files) {
		TravelPlaceRdo rdo = new TravelPlaceRdo();
		rdo.setPlaceKey(travelPlace.getPlaceKey());
		rdo.setName(travelPlace.getName());
		rdo.setCategory(travelPlace.getCategory());
		rdo.setAddress(travelPlace.getAddress());
		rdo.setMemo(travelPlace.getMemo());
		rdo.setDescription(travelPlace.getDescription() != null ? travelPlace.getDescription() : travelPlace.getMemo());
		rdo.setSortOrder(travelPlace.getSortOrder());
		rdo.setCover(coverOf(files, FileBoxTargetType.TRAVEL_PLACE, travelPlace.getPlaceKey()));
		rdo.setPhotos(photosOf(files, FileBoxTargetType.TRAVEL_PLACE, travelPlace.getPlaceKey()));
		return rdo;
	}

	public TravelReviewRdo toTravelReviewRdo(TravelReview travelReview) {
		TravelReviewRdo rdo = new TravelReviewRdo();
		rdo.setOneLineSummary(travelReview.getOneLineSummary());
		rdo.setGoodPoint(travelReview.getGoodPoint());
		rdo.setBadPoint(travelReview.getBadPoint());
		rdo.setRevisitPlace(travelReview.getRevisitPlace());
		rdo.setFinalReview(travelReview.getFinalReview());
		return rdo;
	}

	private FileBoxItemRdo coverOf(List<FileBoxItemRdo> files, FileBoxTargetType targetType, String targetId) {
		return files.stream()
			.filter(file -> targetType == file.getTargetType())
			.filter(file -> equalsTargetId(targetId, file.getTargetId()))
			.filter(file -> FileBoxItemRole.COVER == file.getRole())
			.findFirst()
			.orElse(null);
	}

	private List<FileBoxItemRdo> photosOf(List<FileBoxItemRdo> files, FileBoxTargetType targetType, String targetId) {
		return files.stream()
			.filter(file -> targetType == file.getTargetType())
			.filter(file -> equalsTargetId(targetId, file.getTargetId()))
			.filter(file -> FileBoxItemRole.GALLERY == file.getRole())
			.toList();
	}

	private boolean equalsTargetId(String expected, String actual) {
		if (expected == null) {
			return actual == null;
		}
		return expected.equals(actual);
	}

	private String oneLineReviewOf(TravelReview review) {
		return review == null ? null : review.getOneLineSummary();
	}
}
