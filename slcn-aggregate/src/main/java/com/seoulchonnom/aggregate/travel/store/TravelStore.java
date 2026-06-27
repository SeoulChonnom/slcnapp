package com.seoulchonnom.aggregate.travel.store;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.travel.exception.TravelNotFoundException;
import com.seoulchonnom.aggregate.travel.store.mapper.TravelJpoMapper;
import com.seoulchonnom.aggregate.travel.store.repository.TravelDayRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelPhotoRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelPlaceRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelReviewRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelTagRepository;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.TravelDay;
import com.seoulchonnom.spec.travel.entity.TravelPhoto;
import com.seoulchonnom.spec.travel.entity.TravelPlace;
import com.seoulchonnom.spec.travel.entity.TravelReview;
import com.seoulchonnom.spec.travel.entity.TravelTag;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TravelStore {
	private final TravelRepository travelRepository;
	private final TravelDayRepository travelDayRepository;
	private final TravelPlaceRepository travelPlaceRepository;
	private final TravelPhotoRepository travelPhotoRepository;
	private final TravelTagRepository travelTagRepository;
	private final TravelReviewRepository travelReviewRepository;
	private final TravelJpoMapper travelJpoMapper;

	public Travel save(Travel travel) {
		return travelJpoMapper.toDomain(travelRepository.save(travelJpoMapper.toJpo(travel)));
	}

	public void saveDays(List<TravelDay> travelDays) {
		travelDayRepository.saveAll(travelDays.stream().map(travelJpoMapper::toJpo).toList());
	}

	public TravelDay save(TravelDay travelDay) {
		return travelJpoMapper.toDomain(travelDayRepository.save(travelJpoMapper.toJpo(travelDay)));
	}

	public TravelPlace save(TravelPlace travelPlace) {
		return travelJpoMapper.toDomain(travelPlaceRepository.save(travelJpoMapper.toJpo(travelPlace)));
	}

	public TravelPhoto save(TravelPhoto travelPhoto) {
		return travelJpoMapper.toDomain(travelPhotoRepository.save(travelJpoMapper.toJpo(travelPhoto)));
	}

	public TravelTag save(TravelTag travelTag) {
		return travelJpoMapper.toDomain(travelTagRepository.save(travelJpoMapper.toJpo(travelTag)));
	}

	public TravelReview save(TravelReview travelReview) {
		return travelJpoMapper.toDomain(travelReviewRepository.save(travelJpoMapper.toJpo(travelReview)));
	}

	public List<Travel> findAllVisible() {
		return travelRepository.findAllByHiddenFalseOrderByStartDateDesc()
			.stream()
			.map(travelJpoMapper::toDomain)
			.toList();
	}

	public Travel findById(String travelId) {
		return travelJpoMapper.toDomain(travelRepository.findById(travelId).orElseThrow(TravelNotFoundException::new));
	}

	public Optional<TravelReview> findReviewByTravelId(String travelId) {
		return travelReviewRepository.findByTravelId(travelId).map(travelJpoMapper::toDomain);
	}

	public List<TravelDay> findDaysByTravelId(String travelId) {
		return travelDayRepository.findAllByTravelIdOrderBySortOrderAsc(travelId)
			.stream()
			.map(travelJpoMapper::toDomain)
			.toList();
	}

	public List<TravelDay> findDaysByTravelIdAndDateIn(String travelId, Collection<LocalDate> dates) {
		return travelDayRepository.findAllByTravelIdAndDateIn(travelId, dates)
			.stream()
			.map(travelJpoMapper::toDomain)
			.toList();
	}

	public List<TravelPlace> findPlacesByTravelId(String travelId) {
		return travelPlaceRepository.findAllByTravelIdOrderByTravelDayIdAscSortOrderAsc(travelId)
			.stream()
			.map(travelJpoMapper::toDomain)
			.toList();
	}

	public List<TravelPhoto> findPhotosByTravelId(String travelId) {
		return travelPhotoRepository.findAllByTravelIdOrderByTravelDayIdAscTravelPlaceIdAscSortOrderAsc(travelId)
			.stream()
			.map(travelJpoMapper::toDomain)
			.toList();
	}

	public List<TravelTag> findTagsByTravelId(String travelId) {
		return travelTagRepository.findAllByTravelIdOrderBySortOrderAscRegisteredTimeAsc(travelId)
			.stream()
			.map(travelJpoMapper::toDomain)
			.toList();
	}

	public boolean existsPlaceByDayIds(Collection<String> travelDayIds) {
		return !travelDayIds.isEmpty() && travelPlaceRepository.existsByTravelDayIdIn(travelDayIds);
	}

	public boolean existsPhotoByDayIds(Collection<String> travelDayIds) {
		return !travelDayIds.isEmpty() && travelPhotoRepository.existsByTravelDayIdIn(travelDayIds);
	}

	public void deleteTravel(Travel travel) {
		List<TravelDay> days = findDaysByTravelId(travel.getId());
		List<String> dayIds = days.stream().map(TravelDay::getId).toList();
		deletePhotosByTravelId(travel.getId());
		deletePlacesByDayIds(dayIds);
		travelTagRepository.deleteAll(travelTagRepository.findAllByTravelIdOrderBySortOrderAscRegisteredTimeAsc(travel.getId()));
		travelReviewRepository.findByTravelId(travel.getId()).ifPresent(travelReviewRepository::delete);
		travelDayRepository.deleteAll(days.stream().map(travelJpoMapper::toJpo).toList());
		travelRepository.delete(travelJpoMapper.toJpo(travel));
	}

	public void deleteDays(List<TravelDay> travelDays) {
		travelDayRepository.deleteAll(travelDays.stream().map(travelJpoMapper::toJpo).toList());
	}

	public void deletePlacesByDayIds(Collection<String> travelDayIds) {
		if (travelDayIds.isEmpty()) {
			return;
		}
		travelPlaceRepository.deleteAll(travelPlaceRepository.findAllByTravelDayIdIn(travelDayIds));
	}

	public void deletePhotosByDayIds(Collection<String> travelDayIds) {
		if (travelDayIds.isEmpty()) {
			return;
		}
		travelPhotoRepository.deleteAll(travelPhotoRepository.findAllByTravelDayIdIn(travelDayIds));
	}

	public void deletePhotosByTravelId(String travelId) {
		travelPhotoRepository.deleteAll(travelPhotoRepository.findAllByTravelIdOrderByTravelDayIdAscTravelPlaceIdAscSortOrderAsc(travelId));
	}

	public void deleteTravelLevelPhotosByTravelId(String travelId) {
		travelPhotoRepository.deleteAll(
			travelPhotoRepository.findAllByTravelIdAndTravelDayIdIsNullAndTravelPlaceIdIsNull(travelId));
	}

	public void deleteTagsByTravelId(String travelId) {
		travelTagRepository.deleteAll(travelTagRepository.findAllByTravelIdOrderBySortOrderAscRegisteredTimeAsc(travelId));
		travelTagRepository.flush();
	}
}
