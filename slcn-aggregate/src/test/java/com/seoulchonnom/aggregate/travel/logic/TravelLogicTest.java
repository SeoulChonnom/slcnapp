package com.seoulchonnom.aggregate.travel.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.travel.exception.TravelPeriodConflictException;
import com.seoulchonnom.aggregate.travel.store.TravelStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.vo.TravelDay;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlace;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;
import com.seoulchonnom.spec.travel.mapper.TravelMapper;

class TravelLogicTest {
	private final TravelStore travelStore = mock(TravelStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final TravelMapper travelMapper = mock(TravelMapper.class);
	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final FileBoxMapper fileBoxMapper = new FileBoxMapper();
	private final TravelLogic travelLogic = new TravelLogic(travelStore, idGenerator, travelMapper, fileBoxStore,
		fileAssetStore, fileBoxMapper);

	@Test
	void registerTravel_shouldStoreRootWithDaysAndSyncFileBox() {
		TravelCdo cdo = new TravelCdo("서울", "서울", "2026-06-01", "2026-06-02", List.of("맛집"));
		cdo.setFiles(List.of(new FileBoxItemCdo("travel-cover", FileBoxTargetType.TRAVEL, null,
			FileBoxItemRole.COVER, null, 1)));
		when(idGenerator.nextDomainId("TRAVEL")).thenReturn("TRAVEL-0001");
		when(travelStore.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));
		stubTravelFile("travel-cover");
		stubDetailRdo("TRAVEL-0001", cdo.getFiles().stream().map(fileBoxMapper::toFileBoxItem).toList());

		travelLogic.registerTravel(cdo);

		ArgumentCaptor<Travel> travelCaptor = ArgumentCaptor.forClass(Travel.class);
		verify(travelStore).save(travelCaptor.capture());
		Travel savedTravel = travelCaptor.getValue();
		assertThat(savedTravel.getId()).isEqualTo("TRAVEL-0001");
		assertThat(savedTravel.getDays()).extracting(TravelDay::getDayNumber).containsExactly(1, 2);
		assertThat(savedTravel.getTags()).containsExactly("맛집");
		verify(fileBoxStore).syncItems(eq(FileBoxOwnerType.TRAVEL), eq("TRAVEL-0001"), argThat(items ->
			items.size() == 1 && FileBoxItemRole.COVER == items.get(0).getRole()));
	}

	@Test
	void registerTravel_shouldApplyDayAndPlacePayloads() {
		String placeKey = UUID.randomUUID().toString();
		TravelCdo cdo = new TravelCdo("강릉", "강릉", "2026-06-01", "2026-06-02", List.of("바다"));
		TravelDayUdo dayUdo = new TravelDayUdo("1일차", "바다 산책", 1);
		dayUdo.setDate("2026-06-01");
		dayUdo.setPlaces(List.of(new TravelPlaceUdo(placeKey, "안목해변", TravelPlaceCategory.TOURIST_SPOT,
			"강릉", "메모", null, 1)));
		cdo.setTravelDays(List.of(dayUdo));
		cdo.setFiles(List.of(
			new FileBoxItemCdo("travel-cover", FileBoxTargetType.TRAVEL, null, FileBoxItemRole.COVER, null, 1),
			new FileBoxItemCdo("place-cover", FileBoxTargetType.TRAVEL_PLACE, placeKey, FileBoxItemRole.COVER, null,
				1)));
		when(idGenerator.nextDomainId("TRAVEL")).thenReturn("TRAVEL-0001");
		when(travelStore.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));
		stubTravelFile("travel-cover");
		stubTravelFile("place-cover");
		stubDetailRdo("TRAVEL-0001", cdo.getFiles().stream().map(fileBoxMapper::toFileBoxItem).toList());

		travelLogic.registerTravel(cdo);

		ArgumentCaptor<Travel> travelCaptor = ArgumentCaptor.forClass(Travel.class);
		verify(travelStore).save(travelCaptor.capture());
		TravelDay firstDay = travelCaptor.getValue().getDays().get(0);
		assertThat(firstDay.getTitle()).isEqualTo("1일차");
		assertThat(firstDay.getPlaces()).extracting(TravelPlace::getPlaceKey).containsExactly(placeKey);
	}

	@Test
	void registerTravel_shouldRejectWithoutRootCover() {
		TravelCdo cdo = new TravelCdo("서울", "서울", "2026-06-01", "2026-06-02", null);
		cdo.setFiles(List.of());
		when(idGenerator.nextDomainId("TRAVEL")).thenReturn("TRAVEL-0001");

		assertThatThrownBy(() -> travelLogic.registerTravel(cdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("여행 대표 이미지는 1개여야 합니다.");
	}

	@Test
	void modifyTravel_shouldRejectPeriodShrinkWhenDeletedDayHasFileWithoutConfirm() {
		Travel travel = travelWithDays();
		FileBoxItem deletedDayFile = FileBoxItem.builder()
			.id("item-1")
			.fileAssetId("day-file")
			.targetType(FileBoxTargetType.TRAVEL_DAY)
			.targetId("2026-06-03")
			.role(FileBoxItemRole.GALLERY)
			.sortOrder(1)
			.build();
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, "travel-1"))
			.thenReturn(Optional.of(fileBox("travel-1", List.of(deletedDayFile))));
		TravelUdo udo = new TravelUdo("서울", "서울", "2026-06-01", "2026-06-02", null, false);

		assertThatThrownBy(() -> travelLogic.modifyTravel("travel-1", udo))
			.isInstanceOf(TravelPeriodConflictException.class);
	}

	@Test
	void modifyTravel_shouldPreserveExistingItemIdsWhenPayloadReferencesOwnerItem() {
		Travel travel = travelWithDays();
		FileBoxItem existingCover = FileBoxItem.builder()
			.id("item-cover")
			.fileAssetId("travel-cover")
			.targetType(FileBoxTargetType.TRAVEL)
			.role(FileBoxItemRole.COVER)
			.sortOrder(1)
			.build();
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, "travel-1"))
			.thenReturn(Optional.of(fileBox("travel-1", List.of(existingCover))));
		when(fileBoxStore.syncItems(eq(FileBoxOwnerType.TRAVEL), eq("travel-1"), anyList()))
			.thenAnswer(invocation -> fileBox("travel-1", invocation.getArgument(2)));
		stubTravelFile("travel-cover");
		stubDetailRdo("travel-1", List.of(existingCover));
		TravelUdo udo = new TravelUdo("서울", "서울", "2026-06-01", "2026-06-03", null, null);
		udo.setFiles(List.of(new FileBoxItemUdo("item-cover", "travel-cover", FileBoxTargetType.TRAVEL, null,
			FileBoxItemRole.COVER, null, 1)));

		travelLogic.modifyTravel("travel-1", udo);

		verify(fileBoxStore).syncItems(eq(FileBoxOwnerType.TRAVEL), eq("travel-1"), argThat(items ->
			items.size() == 1 && "item-cover".equals(items.get(0).getId())));
	}

	private Travel travelWithDays() {
		Travel travel = Travel.builder()
			.title("서울")
			.region("서울")
			.startDate(LocalDate.of(2026, 6, 1))
			.endDate(LocalDate.of(2026, 6, 3))
			.days(List.of(
				day("2026-06-01", 1),
				day("2026-06-02", 2),
				day("2026-06-03", 3)))
			.build();
		travel.setId("travel-1");
		return travel;
	}

	private TravelDay day(String date, int dayNumber) {
		return TravelDay.builder()
			.date(LocalDate.parse(date))
			.dayNumber(dayNumber)
			.sortOrder(dayNumber)
			.places(List.of())
			.build();
	}

	private void stubTravelFile(String fileId) {
		when(fileAssetStore.findById(fileId)).thenReturn(fileAsset(fileId, FileType.TRAVEL));
	}

	private void stubDetailRdo(String ownerId, List<FileBoxItem> items) {
		when(fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, ownerId))
			.thenReturn(Optional.of(fileBox(ownerId, items)));
		when(travelMapper.toTravelDetailRdo(any(), anyList())).thenReturn(new TravelDetailRdo());
	}

	private FileAsset fileAsset(String fileId, FileType fileType) {
		FileAsset fileAsset = new FileAsset(fileType, fileId + ".png", fileId + "-stored.png", "image/png", 10L);
		fileAsset.setId(fileId);
		return fileAsset;
	}

	private FileBox fileBox(String ownerId, List<FileBoxItem> items) {
		return FileBox.builder()
			.ownerType(FileBoxOwnerType.TRAVEL)
			.ownerId(ownerId)
			.items(items)
			.build();
	}
}
