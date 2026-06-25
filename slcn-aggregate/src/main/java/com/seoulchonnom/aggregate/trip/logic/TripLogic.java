package com.seoulchonnom.aggregate.trip.logic;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.trip.exception.InvalidTripRegisterException;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
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
	private final FileAssetStore fileAssetStore;

	public List<TripListRdo> getAllTripList() {
		return tripStore.findAllByOrderByDateDesc().stream().map(this::toTripListRdo).toList();
	}

	public TripDetailRdo getTripById(String tripId) {
		return toTripDetailRdo(tripStore.findById(tripId));
	}

	@Transactional
	public TripDetailRdo registerTrip(TripCdo tripCdo) {
		validateTrip(tripCdo);

		String nextTripId = idGenerator.nextDomainId(SequenceName.TRIP.toString());
		Trip trip = new Trip(
			nextTripId,
			tripCdo.getDate(),
			tripCdo.getType(),
			tripCdo.getName(),
			tripCdo.getLogoFileId().trim(),
			tripCdo.getFirstMapFileId().trim(),
			trimToNull(tripCdo.getSecondMapFileId()),
			tripCdo.getNextButtonText(),
			tripCdo.getPreviousButtonText(),
			tripCdo.getDriveUrl(),
			tripMapper.toQuiz(tripCdo.getQuiz())
		);
		tripStore.saveTrip(trip);
		return toTripDetailRdo(trip);
	}

	public QuizRdo getTripQuiz(String tripId) {
		return tripMapper.toQuizRdo(tripStore.findById(tripId).getQuiz());
	}

	public QuizResultRdo checkTripQuizAnswer(String tripId, String optionId) {
		Quiz quiz = tripStore.findById(tripId).getQuiz();
		return tripMapper.toQuizDetailRdo(quiz, optionId);
	}

	private void validateTrip(TripCdo tripCdo) {
		if (tripCdo.getQuiz() == null ||
			tripCdo.getQuiz().getOptions() == null ||
			tripCdo.getQuiz().getOptions().isEmpty()) {
			throw new InvalidTripRegisterException();
		}

		long correctOptionCount = tripCdo.getQuiz().getOptions().stream()
			.filter(OptionCdo::isCorrect)
			.count();
		if (correctOptionCount != 1L) {
			throw new InvalidTripRegisterException();
		}

		validateFileTypes(tripCdo);

		boolean hasSecondMap = StringUtils.hasText(tripCdo.getSecondMapFileId());
		boolean hasNextButtonText = StringUtils.hasText(tripCdo.getNextButtonText());
		boolean hasPreviousButtonText = StringUtils.hasText(tripCdo.getPreviousButtonText());

		boolean allNavigationFieldsPresent = hasSecondMap && hasNextButtonText && hasPreviousButtonText;
		boolean allNavigationFieldsAbsent = !hasSecondMap && !hasNextButtonText && !hasPreviousButtonText;

		if (!(allNavigationFieldsPresent || allNavigationFieldsAbsent)) {
			throw new InvalidTripRegisterException();
		}
	}

	private void validateFileTypes(TripCdo tripCdo) {
		if (!isType(tripCdo.getLogoFileId(), FileType.LOGO) ||
			!isType(tripCdo.getFirstMapFileId(), FileType.MAP) ||
			(StringUtils.hasText(tripCdo.getSecondMapFileId()) && !isType(tripCdo.getSecondMapFileId(), FileType.MAP))) {
			throw new InvalidTripRegisterException();
		}
	}

	private boolean isType(String fileId, FileType type) {
		if (!StringUtils.hasText(fileId)) {
			return false;
		}
		return type.equals(fileAssetStore.findById(fileId.trim()).getType());
	}

	private TripListRdo toTripListRdo(Trip trip) {
		return tripMapper.toTripListRdo(trip, toFileAssetRdo(trip.getLogoFileId()));
	}

	private TripDetailRdo toTripDetailRdo(Trip trip) {
		return tripMapper.toTripDetailRdo(
			trip,
			toFileAssetRdo(trip.getLogoFileId()),
			toFileAssetRdo(trip.getFirstMapFileId()),
			toOptionalFileAssetRdo(trip.getSecondMapFileId())
		);
	}

	private FileAssetRdo toFileAssetRdo(String fileId) {
		FileAsset fileAsset = fileAssetStore.findById(fileId);
		return FileAssetRdo.from(fileAsset);
	}

	private FileAssetRdo toOptionalFileAssetRdo(String fileId) {
		if (!StringUtils.hasText(fileId)) {
			return null;
		}
		return toFileAssetRdo(fileId);
	}

	private String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}
}
