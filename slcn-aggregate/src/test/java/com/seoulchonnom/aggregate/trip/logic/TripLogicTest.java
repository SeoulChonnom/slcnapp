package com.seoulchonnom.aggregate.trip.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.trip.exception.InvalidTripRegisterException;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.mapper.TripMapper;

class TripLogicTest {
	private final TripStore tripStore = mock(TripStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final TripMapper tripMapper = mock(TripMapper.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final FileBoxMapper fileBoxMapper = new FileBoxMapper();
	private final TripLogic tripLogic = new TripLogic(tripStore, idGenerator, tripMapper, fileAssetStore, fileBoxStore,
		fileBoxMapper);

	@Test
	void registerTrip_shouldPersistTripAndSyncFileBoxItems() {
		TripCdo tripCdo = createValidTripCdo();
		stubValidFileAssets();
		stubQuizMapper();
		when(idGenerator.nextDomainId("TRIP")).thenReturn("TRIP-0001");
		when(fileBoxStore.syncItems(eq(FileBoxOwnerType.TRIP), eq("TRIP-0001"), anyList()))
			.thenAnswer(invocation -> fileBox(invocation.getArgument(2)));
		when(fileBoxStore.findByOwner(FileBoxOwnerType.TRIP, "TRIP-0001"))
			.thenAnswer(invocation -> fileBox(tripCdo.getFiles().stream().map(fileBoxMapper::toFileBoxItem).toList()));
		when(tripMapper.toTripDetailRdo(any(), any(), any(), any())).thenReturn(new TripDetailRdo());

		tripLogic.registerTrip(tripCdo);

		ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
		verify(tripStore).saveTrip(tripCaptor.capture());
		assertThat(tripCaptor.getValue().getId()).isEqualTo("TRIP-0001");
		assertThat(tripCaptor.getValue().getQuiz().getCorrectOptionId()).isEqualTo("OPT-2");
		verify(fileBoxStore).syncItems(eq(FileBoxOwnerType.TRIP), eq("TRIP-0001"), argThat(items ->
			items.size() == 2
				&& items.stream().anyMatch(item -> FileBoxItemRole.LOGO == item.getRole())
				&& items.stream().anyMatch(item -> FileBoxItemRole.FIRST_MAP == item.getRole())));
	}

	@Test
	void registerTrip_shouldRejectPartialNavigationFields() {
		TripCdo tripCdo = createValidTripCdo();
		tripCdo.getFiles().add(new FileBoxItemCdo("map-file-2", FileBoxTargetType.TRIP, null,
			FileBoxItemRole.SECOND_MAP, null, 3));
		tripCdo.setNextButtonText("다음");
		stubValidFileAssets();

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	@Test
	void registerTrip_shouldRejectWrongFileType() {
		TripCdo tripCdo = createValidTripCdo();
		when(fileAssetStore.findById("logo-file-1")).thenReturn(fileAsset("logo-file-1", FileType.MAP));
		when(fileAssetStore.findById("map-file-1")).thenReturn(fileAsset("map-file-1", FileType.MAP));

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	private TripCdo createValidTripCdo() {
		return new TripCdo(
			"2026-04-16",
			"ryu",
			"봄 나들이",
			null,
			null,
			"https://drive.example",
			new QuizCdo(
				"퀴즈 제목",
				"정답 제목",
				"정답 설명",
				"오답 제목",
				"오답 설명",
				List.of(
					new OptionCdo("오답", false),
					new OptionCdo("정답", true))),
			new ArrayList<>(List.of(
				new FileBoxItemCdo("logo-file-1", FileBoxTargetType.TRIP, null, FileBoxItemRole.LOGO, null, 1),
				new FileBoxItemCdo("map-file-1", FileBoxTargetType.TRIP, null, FileBoxItemRole.FIRST_MAP, null, 2))));
	}

	private void stubValidFileAssets() {
		when(fileAssetStore.findById("logo-file-1")).thenReturn(fileAsset("logo-file-1", FileType.LOGO));
		when(fileAssetStore.findById("map-file-1")).thenReturn(fileAsset("map-file-1", FileType.MAP));
		when(fileAssetStore.findById("map-file-2")).thenReturn(fileAsset("map-file-2", FileType.MAP));
	}

	private void stubQuizMapper() {
		when(tripMapper.toQuiz(any(QuizCdo.class))).thenCallRealMethod();
	}

	private FileAsset fileAsset(String fileId, FileType fileType) {
		FileAsset fileAsset = new FileAsset(fileType, fileId + ".png", fileId + "-stored.png", "image/png", 10L);
		fileAsset.setId(fileId);
		return fileAsset;
	}

	private FileBox fileBox(List<FileBoxItem> items) {
		return FileBox.builder()
			.ownerType(FileBoxOwnerType.TRIP)
			.ownerId("TRIP-0001")
			.items(items)
			.build();
	}
}
