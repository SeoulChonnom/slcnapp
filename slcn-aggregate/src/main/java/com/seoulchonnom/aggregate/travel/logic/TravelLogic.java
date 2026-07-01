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
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.travel.exception.TravelPeriodConflictException;
import com.seoulchonnom.aggregate.travel.store.TravelStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.vo.TravelDay;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlace;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;
import com.seoulchonnom.spec.travel.entity.vo.TravelReview;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
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
	private final FileBoxStore fileBoxStore;
	private final FileAssetStore fileAssetStore;
	private final FileBoxMapper fileBoxMapper;

	public List<TravelRdo> getTravels() {
		return travelStore.findAllVisible().stream()
			.map(this::toTravelRdo)
			.toList();
	}

	public TravelDetailRdo getTravel(String travelId) {
		return toDetailRdo(travelStore.findById(travelId));
	}

	@Transactional
	public TravelDetailRdo registerTravel(TravelCdo travelCdo) {
		LocalDate startDate = parseDate(travelCdo.getStartDate(), "startDate");
		LocalDate endDate = parseDate(travelCdo.getEndDate(), "endDate");
		validateTravel(travelCdo.getTitle(), travelCdo.getRegion(), startDate, endDate);

		String nextTravelId = idGenerator.nextDomainId(SequenceName.TRAVEL.toString());
		List<TravelDay> days = createDays(startDate, endDate, Map.of());
		applyDayPayloads(days, travelCdo.getTravelDays(), startDate, endDate);
		Travel travel = new Travel(travelCdo.getTitle().trim(), travelCdo.getRegion().trim(), startDate, endDate);
		travel.setId(nextTravelId);
		travel.setDays(days);
		travel.setTags(normalizeTags(travelCdo.getTags()));
		travel.setReview(toReview(travelCdo.getReview()));

		List<FileBoxItem> fileItems = travelCdo.getFiles() == null ? List.of() : travelCdo.getFiles().stream()
																				 .map(fileBoxMapper::toFileBoxItem)
																				 .toList();
		validateTravelFiles(fileItems, travel);

		travelStore.save(travel);
		fileBoxStore.syncItems(FileBoxOwnerType.TRAVEL, nextTravelId, fileItems);
		return toDetailRdo(travel);
	}

	@Transactional
	public TravelDetailRdo modifyTravel(String travelId, TravelUdo travelUdo) {
		Travel travel = travelStore.findById(travelId);
		FileBox fileBox = fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, travelId)
			.orElseGet(() -> fileBoxStore.createForOwner(FileBoxOwnerType.TRAVEL, travelId));
		LocalDate startDate = parseDate(travelUdo.getStartDate(), "startDate");
		LocalDate endDate = parseDate(travelUdo.getEndDate(), "endDate");
		validateTravel(travelUdo.getTitle(), travelUdo.getRegion(), startDate, endDate);

		Set<LocalDate> targetDates = new HashSet<>(datesBetween(startDate, endDate));
		List<TravelDay> deleteDays = travel.getDays().stream()
			.filter(day -> !targetDates.contains(day.getDate()))
			.toList();
		if (!deleteDays.isEmpty() && !Boolean.TRUE.equals(travelUdo.getConfirmDeleteDays())
			&& hasDeleteDayContent(deleteDays, fileBox.getItems())) {
			String dates = deleteDays.stream()
				.map(day -> day.getDate().toString())
				.sorted()
				.toList()
				.toString();
			throw new TravelPeriodConflictException("삭제될 여행 일자에 작성된 내용이 있습니다. dates=" + dates);
		}

		Map<LocalDate, TravelDay> retainedByDate = new HashMap<>();
		travel.getDays().stream()
			.filter(day -> targetDates.contains(day.getDate()))
			.forEach(day -> retainedByDate.put(day.getDate(), day));
		List<TravelDay> days = createDays(startDate, endDate, retainedByDate);
		applyDayPayloads(days, travelUdo.getTravelDays(), startDate, endDate);

		travel.update(travelUdo.getTitle().trim(), travelUdo.getRegion().trim(), startDate, endDate);
		travel.setDays(days);
		travel.setTags(normalizeTags(travelUdo.getTags()));
		travel.setReview(toReview(travelUdo.getReview()));

		List<FileBoxItem> fileItems = resolveModifyItems(fileBox.getItems(), travelUdo.getFiles(), deleteDays);
		validateTravelFiles(fileItems, travel);
		travelStore.save(travel);
		fileBoxStore.syncItems(FileBoxOwnerType.TRAVEL, travelId, fileItems);
		return toDetailRdo(travel);
	}

	@Transactional
	public void deleteTravel(String travelId) {
		Travel travel = travelStore.findById(travelId);
		fileBoxStore.deleteByOwner(FileBoxOwnerType.TRAVEL, travelId);
		travelStore.deleteTravel(travel);
	}

	private TravelRdo toTravelRdo(Travel travel) {
		FileBox fileBox = fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, travel.getId()).orElse(null);
		FileBoxItem cover = fileBox == null ? null : fileBox.getItems().stream()
													 .filter(item -> FileBoxTargetType.TRAVEL == item.getTargetType())
													 .filter(item -> item.getTargetId() == null)
													 .filter(item -> FileBoxItemRole.COVER == item.getRole())
													 .findFirst()
													 .orElse(null);
		return travelMapper.toTravelRdo(travel, cover == null ? null : toFileBoxItemRdo(cover));
	}

	private TravelDetailRdo toDetailRdo(Travel travel) {
		FileBox fileBox = fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, travel.getId())
			.orElseGet(() -> fileBoxStore.createForOwner(FileBoxOwnerType.TRAVEL, travel.getId()));
		return travelMapper.toTravelDetailRdo(travel, toFileBoxItemRdos(fileBox.getItems()));
	}

	private List<FileBoxItemRdo> toFileBoxItemRdos(List<FileBoxItem> items) {
		return items.stream()
			.sorted(Comparator.comparing(FileBoxItem::getTargetType)
				.thenComparing(item -> item.getTargetId() == null ? "" : item.getTargetId())
				.thenComparing(FileBoxItem::getRole)
				.thenComparingInt(FileBoxItem::getSortOrder))
			.map(this::toFileBoxItemRdo)
			.toList();
	}

	private FileBoxItemRdo toFileBoxItemRdo(FileBoxItem item) {
		FileAsset fileAsset = fileAssetStore.findById(item.getFileAssetId());
		return fileBoxMapper.toFileBoxItemRdo(item, FileAssetRdo.from(fileAsset));
	}

	private void validateTravel(String title, String region, LocalDate startDate, LocalDate endDate) {
		if (!StringUtils.hasText(title)) {
			throw new BadRequestException("title은 필수입니다.");
		}
		if (!StringUtils.hasText(region)) {
			throw new BadRequestException("region은 필수입니다.");
		}
		if (!startDate.isBefore(endDate)) {
			throw new BadRequestException("1박 이상 여행만 등록할 수 있습니다.");
		}
	}

	private void validateTravelFiles(List<FileBoxItem> items, Travel travel) {
		Set<String> itemKeys = new HashSet<>();
		Map<String, Integer> groupSortOrders = new HashMap<>();
		int rootCoverCount = 0;
		Set<String> dayDates = new HashSet<>(travel.getDays().stream().map(day -> day.getDate().toString()).toList());
		Set<String> placeKeys = new HashSet<>();
		travel.getDays().forEach(day -> placesOf(day).forEach(place -> placeKeys.add(place.getPlaceKey())));

		for (FileBoxItem item : items) {
			validateCommonItem(item);
			validateTravelTarget(item, dayDates, placeKeys);
			String duplicateKey = item.getTargetType() + "|" + item.getTargetId() + "|" + item.getRole() + "|"
				+ item.getFileAssetId();
			if (!itemKeys.add(duplicateKey)) {
				throw new BadRequestException("이미 연결된 파일입니다.");
			}
			if (FileBoxTargetType.TRAVEL == item.getTargetType() && item.getTargetId() == null
				&& FileBoxItemRole.COVER == item.getRole()) {
				rootCoverCount++;
			}
			if (!FileType.TRAVEL.equals(fileAssetStore.findById(item.getFileAssetId()).getType())) {
				throw new BadRequestException("여행 파일 타입이 올바르지 않습니다.");
			}
			assignSortOrder(item, groupSortOrders);
		}
		if (rootCoverCount != 1) {
			throw new BadRequestException("여행 대표 이미지는 1개여야 합니다.");
		}
	}

	private void validateCommonItem(FileBoxItem item) {
		if (item == null || !StringUtils.hasText(item.getFileAssetId()) || item.getTargetType() == null
			|| item.getRole() == null) {
			throw new BadRequestException("파일 연결 정보가 올바르지 않습니다.");
		}
		item.setFileAssetId(item.getFileAssetId().trim());
		if (FileBoxItemRole.COVER != item.getRole() && FileBoxItemRole.GALLERY != item.getRole()) {
			throw new BadRequestException("여행 파일 role이 올바르지 않습니다.");
		}
	}

	private void validateTravelTarget(FileBoxItem item, Set<String> dayDates, Set<String> placeKeys) {
		if (FileBoxTargetType.TRAVEL == item.getTargetType()) {
			if (item.getTargetId() != null) {
				throw new BadRequestException("TRAVEL 파일 targetId는 비워야 합니다.");
			}
			return;
		}
		if (!StringUtils.hasText(item.getTargetId())) {
			throw new BadRequestException("파일 targetId는 필수입니다.");
		}
		if (FileBoxTargetType.TRAVEL_DAY == item.getTargetType()) {
			LocalDate date = parseDate(item.getTargetId(), "targetId");
			if (!dayDates.contains(date.toString())) {
				throw new BadRequestException("여행 기간에 포함되지 않은 targetId입니다.");
			}
			item.setTargetId(date.toString());
			return;
		}
		if (FileBoxTargetType.TRAVEL_PLACE == item.getTargetType()) {
			validateUuid(item.getTargetId(), "targetId");
			if (!placeKeys.contains(item.getTargetId())) {
				throw new BadRequestException("존재하지 않는 placeKey입니다.");
			}
			return;
		}
		throw new BadRequestException("여행 파일 targetType이 올바르지 않습니다.");
	}

	private void assignSortOrder(FileBoxItem item, Map<String, Integer> groupSortOrders) {
		String groupKey = item.getTargetType() + "|" + item.getTargetId() + "|" + item.getRole();
		int nextOrder = groupSortOrders.getOrDefault(groupKey, 0) + 1;
		if (item.getSortOrder() <= 0) {
			item.setSortOrder(nextOrder);
		}
		groupSortOrders.put(groupKey, Math.max(nextOrder, item.getSortOrder()));
	}

	private List<FileBoxItem> resolveModifyItems(List<FileBoxItem> existingItems, List<FileBoxItemUdo> itemUdos,
		List<TravelDay> deleteDays) {
		Set<String> deleteDateKeys = new HashSet<>(deleteDays.stream().map(day -> day.getDate().toString()).toList());
		Set<String> deletePlaceKeys = new HashSet<>();
		deleteDays.forEach(day -> placesOf(day).forEach(place -> deletePlaceKeys.add(place.getPlaceKey())));
		List<FileBoxItem> baseItems = itemUdos == null ? existingItems : itemUdos.stream()
																		 .map(fileBoxMapper::toFileBoxItem)
																		 .toList();
		if (itemUdos != null) {
			validateExistingItemIds(existingItems, baseItems);
		}
		return baseItems.stream()
			.filter(item -> !isDeletedTarget(item, deleteDateKeys, deletePlaceKeys))
			.toList();
	}

	private void validateExistingItemIds(List<FileBoxItem> existingItems, List<FileBoxItem> payloadItems) {
		Set<String> existingIds = existingItems.stream()
			.map(FileBoxItem::getId)
			.filter(StringUtils::hasText)
			.collect(java.util.stream.Collectors.toSet());
		for (FileBoxItem item : payloadItems) {
			if (StringUtils.hasText(item.getId()) && !existingIds.contains(item.getId())) {
				throw new BadRequestException("존재하지 않는 FileBox item id입니다.");
			}
		}
	}

	private boolean isDeletedTarget(FileBoxItem item, Set<String> deleteDateKeys, Set<String> deletePlaceKeys) {
		return (FileBoxTargetType.TRAVEL_DAY == item.getTargetType() && deleteDateKeys.contains(item.getTargetId()))
			|| (FileBoxTargetType.TRAVEL_PLACE == item.getTargetType() && deletePlaceKeys.contains(item.getTargetId()));
	}

	private boolean hasDeleteDayContent(List<TravelDay> deleteDays, List<FileBoxItem> items) {
		Set<String> deleteDateKeys = new HashSet<>(deleteDays.stream().map(day -> day.getDate().toString()).toList());
		Set<String> deletePlaceKeys = new HashSet<>();
		for (TravelDay day : deleteDays) {
			if (StringUtils.hasText(day.getTitle()) || StringUtils.hasText(day.getMemo()) || !placesOf(day).isEmpty()) {
				return true;
			}
			placesOf(day).forEach(place -> deletePlaceKeys.add(place.getPlaceKey()));
		}
		return items.stream().anyMatch(item -> isDeletedTarget(item, deleteDateKeys, deletePlaceKeys));
	}

	private void applyDayPayloads(List<TravelDay> days, List<TravelDayUdo> dayPayloads, LocalDate startDate,
		LocalDate endDate) {
		if (dayPayloads == null) {
			return;
		}
		Map<LocalDate, TravelDay> dayByDate = new HashMap<>();
		days.forEach(day -> dayByDate.put(day.getDate(), day));
		Set<LocalDate> payloadDates = new HashSet<>();
		for (TravelDayUdo payload : dayPayloads) {
			LocalDate date = parseDate(payload.getDate(), "date");
			if (date.isBefore(startDate) || date.isAfter(endDate)) {
				throw new BadRequestException("여행 기간에 포함되지 않은 date입니다.");
			}
			if (!payloadDates.add(date)) {
				throw new BadRequestException("date가 중복되었습니다.");
			}
			TravelDay day = dayByDate.get(date);
			day.setTitle(trimToNull(payload.getTitle()));
			day.setMemo(trimToNull(payload.getMemo()));
			if (payload.getSortOrder() != null) {
				day.setSortOrder(payload.getSortOrder());
			}
			if (payload.getPlaces() != null) {
				day.setPlaces(toPlaces(payload.getPlaces()));
			}
		}
		validatePlaceKeys(days);
	}

	private List<TravelPlace> toPlaces(List<TravelPlaceUdo> placePayloads) {
		List<TravelPlace> places = new ArrayList<>();
		int nextSortOrder = 1;
		for (TravelPlaceUdo placePayload : placePayloads) {
			validatePlace(placePayload.getPlaceKey(), placePayload.getName(), placePayload.getCategory());
			int sortOrder = placePayload.getSortOrder() == null ? nextSortOrder : placePayload.getSortOrder();
			places.add(TravelPlace.builder()
				.placeKey(placePayload.getPlaceKey())
				.name(placePayload.getName().trim())
				.category(placePayload.getCategory())
				.address(trimToNull(placePayload.getAddress()))
				.memo(trimToNull(placePayload.getMemo()))
				.description(descriptionOf(placePayload.getDescription(), placePayload.getMemo()))
				.sortOrder(sortOrder)
				.build());
			nextSortOrder = sortOrder + 1;
		}
		return places;
	}

	private void validatePlace(String placeKey, String name, TravelPlaceCategory category) {
		validateUuid(placeKey, "placeKey");
		if (!StringUtils.hasText(name)) {
			throw new BadRequestException("name은 필수입니다.");
		}
		if (category == null) {
			throw new BadRequestException("category는 필수입니다.");
		}
	}

	private void validatePlaceKeys(List<TravelDay> days) {
		Set<String> placeKeys = new HashSet<>();
		for (TravelDay day : days) {
			for (TravelPlace place : placesOf(day)) {
				if (!placeKeys.add(place.getPlaceKey())) {
					throw new BadRequestException("placeKey가 중복되었습니다.");
				}
			}
		}
	}

	private void validateUuid(String value, String fieldName) {
		if (!StringUtils.hasText(value)) {
			throw new BadRequestException(fieldName + "는 필수입니다.");
		}
		try {
			UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException(fieldName + "는 UUID 형식이어야 합니다.");
		}
	}

	private List<String> normalizeTags(List<String> tagNames) {
		if (tagNames == null) {
			return new ArrayList<>();
		}
		Set<String> names = new HashSet<>();
		List<String> normalizedTags = new ArrayList<>();
		for (String tagName : tagNames) {
			String normalized = normalizeTag(tagName);
			if (!names.add(normalized)) {
				throw new BadRequestException("이미 등록된 태그입니다.");
			}
			if (names.size() > MAX_TAG_COUNT) {
				throw new BadRequestException("태그는 최대 10개까지 등록할 수 있습니다.");
			}
			normalizedTags.add(normalized);
		}
		return normalizedTags;
	}

	private TravelReview toReview(TravelReviewUdo udo) {
		if (udo == null) {
			return null;
		}
		return TravelReview.builder()
			.oneLineSummary(trimToNull(udo.getOneLineSummary()))
			.goodPoint(trimToNull(udo.getGoodPoint()))
			.badPoint(trimToNull(udo.getBadPoint()))
			.revisitPlace(trimToNull(udo.getRevisitPlace()))
			.finalReview(trimToNull(udo.getFinalReview()))
			.build();
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
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

	private List<TravelDay> createDays(LocalDate startDate, LocalDate endDate,
		Map<LocalDate, TravelDay> existingByDate) {
		List<TravelDay> travelDays = new ArrayList<>();
		List<LocalDate> dates = datesBetween(startDate, endDate);
		for (int index = 0; index < dates.size(); index++) {
			int dayNumber = index + 1;
			LocalDate date = dates.get(index);
			TravelDay travelDay = existingByDate.getOrDefault(date, TravelDay.builder()
				.date(date)
				.places(new ArrayList<>())
				.build());
			if (travelDay.getPlaces() == null) {
				travelDay.setPlaces(new ArrayList<>());
			}
			travelDay.setDayNumber(dayNumber);
			travelDay.setSortOrder(dayNumber);
			travelDays.add(travelDay);
		}
		return travelDays;
	}

	private List<TravelPlace> placesOf(TravelDay day) {
		return day.getPlaces() == null ? List.of() : day.getPlaces();
	}

	private List<LocalDate> datesBetween(LocalDate startDate, LocalDate endDate) {
		long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
		return java.util.stream.LongStream.range(0, days)
			.mapToObj(startDate::plusDays)
			.sorted(Comparator.naturalOrder())
			.toList();
	}
}
