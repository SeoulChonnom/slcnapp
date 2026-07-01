package com.seoulchonnom.aggregate.trip.logic;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.trip.exception.InvalidTripRegisterException;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
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
	private final FileBoxStore fileBoxStore;
	private final FileBoxMapper fileBoxMapper;

	public List<TripListRdo> getAllTripList() {
		return tripStore.findAllByOrderByDateDesc().stream().map(this::toTripListRdo).toList();
	}

	public TripDetailRdo getTripById(String tripId) {
		return toTripDetailRdo(tripStore.findById(tripId));
	}

	@Transactional
	public TripDetailRdo registerTrip(TripCdo tripCdo) {
		List<FileBoxItem> fileItems = toFileItems(tripCdo);
		validateTrip(tripCdo, fileItems);

		String nextTripId = idGenerator.nextDomainId(SequenceName.TRIP.toString());
		Trip trip = new Trip(
			nextTripId,
			tripCdo.getDate(),
			tripCdo.getType(),
			tripCdo.getName(),
			tripCdo.getNextButtonText(),
			tripCdo.getPreviousButtonText(),
			tripCdo.getDriveUrl(),
			tripMapper.toQuiz(tripCdo.getQuiz())
		);
		tripStore.saveTrip(trip);
		fileBoxStore.syncItems(FileBoxOwnerType.TRIP, nextTripId, fileItems);
		return toTripDetailRdo(trip);
	}

	public QuizRdo getTripQuiz(String tripId) {
		return tripMapper.toQuizRdo(tripStore.findById(tripId).getQuiz());
	}

	public QuizResultRdo checkTripQuizAnswer(String tripId, String optionId) {
		Quiz quiz = tripStore.findById(tripId).getQuiz();
		return tripMapper.toQuizDetailRdo(quiz, optionId);
	}

	private void validateTrip(TripCdo tripCdo, List<FileBoxItem> fileItems) {
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

		Map<FileBoxItemRole, FileBoxItem> roleItems = validateTripFiles(fileItems);
		boolean hasSecondMap = roleItems.containsKey(FileBoxItemRole.SECOND_MAP);
		boolean hasNextButtonText = StringUtils.hasText(tripCdo.getNextButtonText());
		boolean hasPreviousButtonText = StringUtils.hasText(tripCdo.getPreviousButtonText());

		boolean allNavigationFieldsPresent = hasSecondMap && hasNextButtonText && hasPreviousButtonText;
		boolean allNavigationFieldsAbsent = !hasSecondMap && !hasNextButtonText && !hasPreviousButtonText;

		if (!(allNavigationFieldsPresent || allNavigationFieldsAbsent)) {
			throw new InvalidTripRegisterException();
		}
	}

	private Map<FileBoxItemRole, FileBoxItem> validateTripFiles(List<FileBoxItem> fileItems) {
		Map<FileBoxItemRole, FileBoxItem> roleItems = new EnumMap<>(FileBoxItemRole.class);
		for (FileBoxItem item : fileItems) {
			if (!isValidTripItem(item) || roleItems.put(item.getRole(), item) != null) {
				throw new InvalidTripRegisterException();
			}
		}
		if (!roleItems.containsKey(FileBoxItemRole.LOGO) || !roleItems.containsKey(FileBoxItemRole.FIRST_MAP)) {
			throw new InvalidTripRegisterException();
		}
		validateFileType(roleItems.get(FileBoxItemRole.LOGO), FileType.LOGO);
		validateFileType(roleItems.get(FileBoxItemRole.FIRST_MAP), FileType.MAP);
		if (roleItems.containsKey(FileBoxItemRole.SECOND_MAP)) {
			validateFileType(roleItems.get(FileBoxItemRole.SECOND_MAP), FileType.MAP);
		}
		return roleItems;
	}

	private boolean isValidTripItem(FileBoxItem item) {
		return item != null
			&& StringUtils.hasText(item.getFileAssetId())
			&& FileBoxTargetType.TRIP == item.getTargetType()
			&& item.getTargetId() == null
			&& (FileBoxItemRole.LOGO == item.getRole()
			|| FileBoxItemRole.FIRST_MAP == item.getRole()
			|| FileBoxItemRole.SECOND_MAP == item.getRole());
	}

	private void validateFileType(FileBoxItem item, FileType type) {
		if (!type.equals(fileAssetStore.findById(item.getFileAssetId().trim()).getType())) {
			throw new InvalidTripRegisterException();
		}
		item.setFileAssetId(item.getFileAssetId().trim());
	}

	private List<FileBoxItem> toFileItems(TripCdo tripCdo) {
		if (tripCdo.getFiles() == null) {
			throw new InvalidTripRegisterException();
		}
		return tripCdo.getFiles().stream()
			.map(fileBoxMapper::toFileBoxItem)
			.toList();
	}

	private TripListRdo toTripListRdo(Trip trip) {
		FileBoxItem logo = findRoleItem(fileBoxStore.findByOwner(FileBoxOwnerType.TRIP, trip.getId()),
			FileBoxItemRole.LOGO);
		return tripMapper.toTripListRdo(trip, toFileAssetRdo(logo.getFileAssetId()));
	}

	private TripDetailRdo toTripDetailRdo(Trip trip) {
		FileBox fileBox = fileBoxStore.findByOwner(FileBoxOwnerType.TRIP, trip.getId());
		FileBoxItem logo = findRoleItem(fileBox, FileBoxItemRole.LOGO);
		FileBoxItem firstMap = findRoleItem(fileBox, FileBoxItemRole.FIRST_MAP);
		FileBoxItem secondMap = findOptionalRoleItem(fileBox, FileBoxItemRole.SECOND_MAP);
		return tripMapper.toTripDetailRdo(
			trip,
			toFileAssetRdo(logo.getFileAssetId()),
			toFileAssetRdo(firstMap.getFileAssetId()),
			secondMap == null ? null : toFileAssetRdo(secondMap.getFileAssetId())
		);
	}

	private FileBoxItem findRoleItem(FileBox fileBox, FileBoxItemRole role) {
		FileBoxItem item = findOptionalRoleItem(fileBox, role);
		if (item == null) {
			throw new InvalidTripRegisterException();
		}
		return item;
	}

	private FileBoxItem findOptionalRoleItem(FileBox fileBox, FileBoxItemRole role) {
		return fileBox.getItems().stream()
			.filter(item -> role == item.getRole())
			.findFirst()
			.orElse(null);
	}

	private FileAssetRdo toFileAssetRdo(String fileId) {
		FileAsset fileAsset = fileAssetStore.findById(fileId);
		return FileAssetRdo.from(fileAsset);
	}
}
