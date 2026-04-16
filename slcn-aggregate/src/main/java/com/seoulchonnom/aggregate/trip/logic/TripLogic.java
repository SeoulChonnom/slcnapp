package com.seoulchonnom.aggregate.trip.logic;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.trip.exception.InvalidTripRegisterException;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;
import com.seoulchonnom.spec.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripLogic {
	private final TripStore tripStore;
	private final IdGenerator idGenerator;
	private final TripMapper tripMapper;

	public List<TripListRdo> getAllTripList() {
		return tripStore.findAllByOrderByDateDesc().stream().map(tripMapper::toTripListRdo).toList();
	}

	public TripInfoRdo getTripInfoByDate(String tripDate) {
		return tripMapper.toTripInfoRdo(tripStore.findByDate(tripDate));
	}

	@Transactional
	public TripInfoRdo registerTrip(TripCdo tripCdo) {
		String nextTripId = idGenerator.nextDomainId(SequenceName.TRIP.toString());

		validateTrip(tripCdo);

		Trip trip = new Trip(tripCdo, nextTripId);
		tripStore.saveTrip(trip);
		return tripMapper.toTripInfoRdo(trip);
	}

	private void validateTrip(TripCdo tripCdo) {
		boolean quizAnswerMatched = tripCdo.getQuizCdoList().stream()
			.map(QuizCdo::getQuizIndex)
			.anyMatch(tripCdo.getQuizAnswer()::equals);

		if (!quizAnswerMatched) {
			throw new InvalidTripRegisterException();
		}

		boolean hasSecondMap = StringUtils.hasText(tripCdo.getSecondMap());
		boolean hasNextButtonText = StringUtils.hasText(tripCdo.getNextButtonText());
		boolean hasPreviousButtonText = StringUtils.hasText(tripCdo.getPreviousButtonText());

		boolean allNavigationFieldsPresent = hasSecondMap && hasNextButtonText && hasPreviousButtonText;
		boolean allNavigationFieldsAbsent = !hasSecondMap && !hasNextButtonText && !hasPreviousButtonText;

		if (!(allNavigationFieldsPresent || allNavigationFieldsAbsent)) {
			throw new InvalidTripRegisterException();
		}
	}
}
