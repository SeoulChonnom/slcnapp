package com.seoulchonnom.aggregate.travel.logic;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.travel.exception.TravelPeriodConflictException;
import com.seoulchonnom.aggregate.travel.store.TravelStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.TravelDay;
import com.seoulchonnom.spec.travel.entity.TravelPhoto;
import com.seoulchonnom.spec.travel.entity.TravelPlace;
import com.seoulchonnom.spec.travel.entity.TravelReview;
import com.seoulchonnom.spec.travel.entity.TravelTag;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPhotoCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;
import com.seoulchonnom.spec.travel.mapper.TravelMapper;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TravelLogic {
	private static final int MAX_TAG_COUNT = 10;

	private final TravelStore travelStore;
	private final IdGenerator idGenerator;
	private final TravelMapper travelMapper;

	public List<TravelRdo> getTravels() {
		return travelStore.findAllVisible().stream()
			.map(travel -> travelMapper.toTravelRdo(travel, travelStore.findTagsByTravelId(travel.getId()),
				travelStore.findReviewByTravelId(travel.getId()).orElse(null)))
			.toList();
	}

	public TravelDetailRdo getTravel(String travelId) {
		return toDetailRdo(travelStore.findById(travelId));
	}

	@Transactional
	public TravelDetailRdo registerTravel(TravelCdo travelCdo) {
		LocalDate startDate = parseDate(travelCdo.getStartDate(), "startDate");
		LocalDate endDate = parseDate(travelCdo.getEndDate(), "endDate");
		validateTravel(travelCdo.getTitle(), travelCdo.getRegion(), travelCdo.getCoverPhotoId(), startDate, endDate);

		String nextTravelId = idGenerator.nextDomainId(SequenceName.TRAVEL.toString());
		Travel travel = new Travel(travelCdo.getTitle().trim(), travelCdo.getRegion().trim(), startDate, endDate,
			travelCdo.getCoverPhotoId().trim());
		travel.setId(nextTravelId);
		travel = travelStore.save(travel);
		travelStore.saveDays(createDays(travel.getId(), startDate, endDate, Map.of()));
		syncTravelDetails(travel.getId(), travelCdo.getTravelDays(), travelCdo.getPhotos(), travelCdo.getReview());
		saveTags(travel.getId(), travelCdo.getTags());
		return toDetailRdo(travelStore.findById(travel.getId()));
	}

	@Transactional
	public TravelDetailRdo modifyTravel(String travelId, TravelUdo travelUdo) {
		Travel travel = travelStore.findById(travelId);
		LocalDate startDate = parseDate(travelUdo.getStartDate(), "startDate");
		LocalDate endDate = parseDate(travelUdo.getEndDate(), "endDate");
		validateTravel(travelUdo.getTitle(), travelUdo.getRegion(), travelUdo.getCoverPhotoId(), startDate, endDate);

		List<TravelDay> existingDays = travelStore.findDaysByTravelId(travelId);
		Set<LocalDate> targetDates = new HashSet<>(datesBetween(startDate, endDate));
		List<TravelDay> deleteDays = existingDays.stream()
			.filter(day -> !targetDates.contains(day.getDate()))
			.toList();
		List<String> deleteDayIds = deleteDays.stream().map(TravelDay::getId).toList();

		if (!deleteDays.isEmpty() && !Boolean.TRUE.equals(travelUdo.getConfirmDeleteDays())
			&& hasDeleteDayContent(deleteDays, deleteDayIds)) {
			String dates = deleteDays.stream()
				.map(day -> day.getDate().toString())
				.sorted()
				.toList()
				.toString();
			throw new TravelPeriodConflictException("삭제될 여행 일자에 작성된 내용이 있습니다. dates=" + dates);
		}

		travelStore.deletePhotosByDayIds(deleteDayIds);
		travelStore.deletePlacesByDayIds(deleteDayIds);
		travelStore.deleteDays(deleteDays);

		Map<LocalDate, TravelDay> retainedByDate = new HashMap<>();
		existingDays.stream()
			.filter(day -> targetDates.contains(day.getDate()))
			.forEach(day -> retainedByDate.put(day.getDate(), day));
		travelStore.saveDays(createDays(travelId, startDate, endDate, retainedByDate));

		travel.update(travelUdo.getTitle().trim(), travelUdo.getRegion().trim(), startDate, endDate,
			travelUdo.getCoverPhotoId().trim());
		travelStore.save(travel);
		saveTags(travelId, travelUdo.getTags());
		if (hasDetailPayload(travelUdo)) {
			syncTravelDetails(travelId, travelUdo.getTravelDays(), travelUdo.getPhotos(), travelUdo.getReview());
		}
		return toDetailRdo(travelStore.findById(travelId));
	}

	@Transactional
	public void deleteTravel(String travelId) {
		Travel travel = travelStore.findById(travelId);
		travelStore.deleteTravel(travel);
	}

	private TravelDetailRdo toDetailRdo(Travel travel) {
		List<TravelDay> days = travelStore.findDaysByTravelId(travel.getId());
		List<TravelPlace> places = travelStore.findPlacesByTravelId(travel.getId());
		List<TravelPhoto> photos = travelStore.findPhotosByTravelId(travel.getId());
		List<TravelTag> tags = travelStore.findTagsByTravelId(travel.getId());
		TravelReview review = travelStore.findReviewByTravelId(travel.getId()).orElse(null);
		return travelMapper.toTravelDetailRdo(travel, days, places, photos, tags, review);
	}

	private void validateTravel(String title, String region, String coverPhotoId, LocalDate startDate, LocalDate endDate) {
		if (!StringUtils.hasText(title)) {
			throw new BadRequestException("title은 필수입니다.");
		}
		if (!StringUtils.hasText(region)) {
			throw new BadRequestException("region은 필수입니다.");
		}
		if (!StringUtils.hasText(coverPhotoId)) {
			throw new BadRequestException("coverPhotoId는 필수입니다.");
		}
		if (!startDate.isBefore(endDate)) {
			throw new BadRequestException("1박 이상 여행만 등록할 수 있습니다.");
		}
	}

	private void validatePlace(String name, TravelPlaceCategory category) {
		if (!StringUtils.hasText(name)) {
			throw new BadRequestException("name은 필수입니다.");
		}
		if (category == null) {
			throw new BadRequestException("category는 필수입니다.");
		}
	}

	private void validatePhotoRequest(TravelPhotoCdo cdo) {
		if (!StringUtils.hasText(cdo.getPhotoFileId())) {
			throw new BadRequestException("photoFileId는 필수입니다.");
		}
	}

	private boolean hasDeleteDayContent(List<TravelDay> deleteDays, List<String> deleteDayIds) {
		return deleteDays.stream().anyMatch(TravelDay::hasContent)
			|| travelStore.existsPlaceByDayIds(deleteDayIds)
			|| travelStore.existsPhotoByDayIds(deleteDayIds);
	}

	private void saveTags(String travelId, List<String> tagNames) {
		travelStore.deleteTagsByTravelId(travelId);
		if (tagNames == null) {
			return;
		}
		int sortOrder = 1;
		Set<String> names = new HashSet<>();
		for (String tagName : tagNames) {
			String normalized = normalizeTag(tagName);
			if (!names.add(normalized)) {
				throw new BadRequestException("이미 등록된 태그입니다.");
			}
			if (names.size() > MAX_TAG_COUNT) {
				throw new BadRequestException("태그는 최대 10개까지 등록할 수 있습니다.");
			}
			travelStore.save(new TravelTag(travelId, normalized, sortOrder++));
		}
	}

	private boolean hasDetailPayload(TravelUdo travelUdo) {
		return travelUdo.getTravelDays() != null || travelUdo.getPhotos() != null || travelUdo.getReview() != null;
	}

	private void syncTravelDetails(String travelId, List<TravelDayUdo> dayPayloads, List<TravelPhotoCdo> travelPhotos,
		TravelReviewUdo reviewPayload) {
		if (dayPayloads == null && travelPhotos == null && reviewPayload == null) {
			return;
		}

		if (dayPayloads != null) {
			List<TravelDay> days = travelStore.findDaysByTravelId(travelId);
			List<String> dayIds = days.stream().map(TravelDay::getId).toList();
			travelStore.deletePhotosByTravelId(travelId);
			travelStore.deletePlacesByDayIds(dayIds);
			syncDays(travelId, days, dayPayloads);
		} else if (travelPhotos != null) {
			travelStore.deleteTravelLevelPhotosByTravelId(travelId);
		}
		if (travelPhotos != null) {
			int sortOrder = 1;
			Set<String> photoKeys = new HashSet<>();
			for (TravelPhotoCdo photoPayload : travelPhotos) {
				if (!StringUtils.hasText(photoPayload.getTravelDayId()) && !StringUtils.hasText(photoPayload.getTravelPlaceId())) {
					savePhotoLink(travelId, null, null, photoPayload, sortOrder++, photoKeys);
				}
			}
		}
		if (reviewPayload != null) {
			saveReview(travelId, reviewPayload);
		}
	}

	private void syncDays(String travelId, List<TravelDay> days, List<TravelDayUdo> dayPayloads) {
		Map<String, TravelDay> dayById = new HashMap<>();
		Map<LocalDate, TravelDay> dayByDate = new HashMap<>();
		for (TravelDay day : days) {
			dayById.put(day.getId(), day);
			dayByDate.put(day.getDate(), day);
		}

		for (int index = 0; index < dayPayloads.size(); index++) {
			TravelDayUdo dayPayload = dayPayloads.get(index);
			TravelDay day = resolveDay(dayPayload, days, dayById, dayByDate, index);
			if (hasDayPayloadContent(dayPayload) && !StringUtils.hasText(dayPayload.getCoverPhotoId())) {
				throw new BadRequestException("coverPhotoId는 필수입니다.");
			}
			day.update(trimToNull(dayPayload.getTitle()), trimToNull(dayPayload.getMemo()),
				trimToNull(dayPayload.getCoverPhotoId()),
				dayPayload.getSortOrder() == null ? day.getSortOrder() : dayPayload.getSortOrder());
			travelStore.save(day);

			Set<String> photoKeys = new HashSet<>();
			if (dayPayload.getPhotos() != null) {
				int photoSortOrder = 1;
				for (TravelPhotoCdo photoPayload : dayPayload.getPhotos()) {
					savePhotoLink(travelId, day.getId(), null, photoPayload, photoSortOrder++, photoKeys);
				}
			}
			if (dayPayload.getPlaces() != null) {
				syncPlaces(travelId, day.getId(), dayPayload.getPlaces());
			}
		}
	}

	private TravelDay resolveDay(TravelDayUdo dayPayload, List<TravelDay> days, Map<String, TravelDay> dayById,
		Map<LocalDate, TravelDay> dayByDate, int index) {
		if (StringUtils.hasText(dayPayload.getId()) && dayById.containsKey(dayPayload.getId())) {
			return dayById.get(dayPayload.getId());
		}
		if (StringUtils.hasText(dayPayload.getDate())) {
			LocalDate date = parseDate(dayPayload.getDate(), "date");
			if (dayByDate.containsKey(date)) {
				return dayByDate.get(date);
			}
			throw new BadRequestException("여행 기간에 포함되지 않은 date입니다.");
		}
		if (index < days.size()) {
			return days.get(index);
		}
		throw new BadRequestException("여행 일자 정보가 올바르지 않습니다.");
	}

	private void syncPlaces(String travelId, String travelDayId, List<TravelPlaceUdo> placePayloads) {
		int placeSortOrder = 1;
		for (TravelPlaceUdo placePayload : placePayloads) {
			validatePlace(placePayload.getName(), placePayload.getCategory());
			int sortOrder = placePayload.getSortOrder() == null ? placeSortOrder : placePayload.getSortOrder();
			TravelPlace place = travelStore.save(new TravelPlace(travelId, travelDayId, placePayload.getName().trim(),
				placePayload.getCategory(), descriptionOf(placePayload.getDescription(), placePayload.getMemo()),
				trimToNull(placePayload.getCoverPhotoId()), sortOrder));
			placeSortOrder = sortOrder + 1;

			if (placePayload.getPhotos() != null) {
				Set<String> photoKeys = new HashSet<>();
				int photoSortOrder = 1;
				for (TravelPhotoCdo photoPayload : placePayload.getPhotos()) {
					savePhotoLink(travelId, travelDayId, place.getId(), photoPayload, photoSortOrder++, photoKeys);
				}
			}
		}
	}

	private void savePhotoLink(String travelId, String travelDayId, String travelPlaceId, TravelPhotoCdo photoPayload,
		int defaultSortOrder, Set<String> photoKeys) {
		validatePhotoRequest(photoPayload);
		String photoFileId = photoPayload.getPhotoFileId().trim();
		String key = (travelDayId == null ? "" : travelDayId) + "|" + (travelPlaceId == null ? "" : travelPlaceId)
			+ "|" + photoFileId;
		if (!photoKeys.add(key)) {
			throw new BadRequestException("이미 연결된 사진입니다.");
		}
		int sortOrder = photoPayload.getSortOrder() == null ? defaultSortOrder : photoPayload.getSortOrder();
		travelStore.save(new TravelPhoto(travelId, travelDayId, travelPlaceId, photoFileId,
			trimToNull(photoPayload.getCaption()), sortOrder));
	}

	private boolean hasDayPayloadContent(TravelDayUdo dayPayload) {
		return StringUtils.hasText(dayPayload.getTitle())
			|| StringUtils.hasText(dayPayload.getMemo())
			|| dayPayload.getPhotos() != null
			|| dayPayload.getPlaces() != null
			|| StringUtils.hasText(dayPayload.getCoverPhotoId());
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private void saveReview(String travelId, TravelReviewUdo udo) {
		TravelReview review = travelStore.findReviewByTravelId(travelId)
			.orElseGet(() -> new TravelReview(travelId, null, null, null, null, null));
		review.update(trimToNull(udo.getOneLineSummary()), trimToNull(udo.getGoodPoint()),
			trimToNull(udo.getBadPoint()),
			trimToNull(udo.getRevisitPlace()), trimToNull(udo.getFinalReview()));
		travelStore.save(review);
	}

	private String descriptionOf(String description, String memo) {
		return trimToNull(description) != null ? trimToNull(description) : trimToNull(memo);
	}

	private String normalizeTag(String name) {
		if (!StringUtils.hasText(name)) {
			throw new BadRequestException("name은 필수입니다.");
		}
		String trimmed = name.trim();
		if (!StringUtils.hasText(trimmed)) {
			throw new BadRequestException("name은 필수입니다.");
		}
		return trimmed;
	}

	private LocalDate parseDate(String value, String fieldName) {
		if (!StringUtils.hasText(value)) {
			throw new BadRequestException(fieldName + "는 필수입니다.");
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			throw new BadRequestException(fieldName + "는 yyyy-MM-dd 형식이어야 합니다.");
		}
	}

	private List<TravelDay> createDays(String travelId, LocalDate startDate, LocalDate endDate,
		Map<LocalDate, TravelDay> existingByDate) {
		List<TravelDay> travelDays = new ArrayList<>();
		List<LocalDate> dates = datesBetween(startDate, endDate);
		for (int index = 0; index < dates.size(); index++) {
			int dayNumber = index + 1;
			LocalDate date = dates.get(index);
			TravelDay travelDay = existingByDate.getOrDefault(date, new TravelDay(travelId, date, dayNumber));
			travelDay.reorder(dayNumber);
			travelDays.add(travelDay);
		}
		return travelDays;
	}

	private List<LocalDate> datesBetween(LocalDate startDate, LocalDate endDate) {
		long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
		return java.util.stream.LongStream.range(0, days)
			.mapToObj(startDate::plusDays)
			.sorted(Comparator.naturalOrder())
			.toList();
	}
}
