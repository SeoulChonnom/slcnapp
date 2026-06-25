package com.seoulchonnom.aggregate.trip.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.trip.exception.InvalidTripRegisterException;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
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
	private final TripLogic tripLogic = new TripLogic(tripStore, idGenerator, tripMapper, fileAssetStore);

	@Test
	void registerTrip_shouldGenerateTripIdAndPersistQuizStructure() {
		TripCdo tripCdo = createValidTripCdo();
		TripDetailRdo tripDetailRdo = new TripDetailRdo();
		stubValidFileAssets();
		when(idGenerator.nextDomainId("TRIP")).thenReturn("TRIP-0001");
		when(tripMapper.toTripDetailRdo(any(Trip.class), any(FileAssetRdo.class), any(FileAssetRdo.class), any()))
			.thenReturn(tripDetailRdo);

		tripLogic.registerTrip(tripCdo);

		ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
		verify(tripStore).saveTrip(tripCaptor.capture());

		Trip savedTrip = tripCaptor.getValue();
		assertThat(savedTrip.getId()).isEqualTo("TRIP-0001");
		assertThat(savedTrip.getLogoFileId()).isEqualTo("logo-file-1");
		assertThat(savedTrip.getFirstMapFileId()).isEqualTo("map-file-1");
		assertThat(savedTrip.getQuiz().getCorrectOptionId()).isEqualTo("OPT-2");
		assertThat(savedTrip.getQuiz().getOptions())
			.extracting(option -> option.getId() + ":" + option.getText())
			.containsExactly("OPT-1:오답", "OPT-2:정답");
	}

	@Test
	void registerTrip_shouldRejectWhenCorrectOptionIsMissing() {
		TripCdo tripCdo = createValidTripCdo();
		tripCdo.getQuiz().getOptions().forEach(option -> option.setCorrect(false));

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
		verify(idGenerator, never()).nextDomainId(anyString());
	}

	@Test
	void registerTrip_shouldRejectWhenMultipleCorrectOptionsExist() {
		TripCdo tripCdo = createValidTripCdo();
		tripCdo.getQuiz().getOptions().get(0).setCorrect(true);

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	@Test
	void registerTrip_shouldRejectWhenNavigationFieldsArePartial() {
		TripCdo tripCdo = createValidTripCdo();
		stubValidFileAssets();
		tripCdo.setSecondMapFileId("map-file-2");
		tripCdo.setNextButtonText("next");
		tripCdo.setPreviousButtonText(null);

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	@Test
	void registerTrip_shouldRejectWhenFileTypeDoesNotMatchTripField() {
		TripCdo tripCdo = createValidTripCdo();
		when(fileAssetStore.findById("logo-file-1")).thenReturn(fileAsset("logo-file-1", FileType.MAP));
		when(fileAssetStore.findById("map-file-1")).thenReturn(fileAsset("map-file-1", FileType.MAP));

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	@Test
	void registerTrip_shouldAllowWhenNavigationFieldsAreFullyProvided() {
		TripCdo tripCdo = createValidTripCdo();
		TripDetailRdo tripDetailRdo = new TripDetailRdo();
		stubValidFileAssets();
		when(idGenerator.nextDomainId("TRIP")).thenReturn("TRIP-0002");
		when(tripMapper.toTripDetailRdo(any(Trip.class), any(FileAssetRdo.class), any(FileAssetRdo.class), any(FileAssetRdo.class)))
			.thenReturn(tripDetailRdo);
		tripCdo.setSecondMapFileId("map-file-2");
		tripCdo.setNextButtonText("다음");
		tripCdo.setPreviousButtonText("이전");

		tripLogic.registerTrip(tripCdo);

		ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
		verify(tripStore).saveTrip(tripCaptor.capture());
		assertThat(tripCaptor.getValue().getSecondMapFileId()).isEqualTo("map-file-2");
		assertThat(tripCaptor.getValue().getNextButtonText()).isEqualTo("다음");
		assertThat(tripCaptor.getValue().getPreviousButtonText()).isEqualTo("이전");
	}

	@Test
	void registerTrip_shouldRejectWhenQuizOptionsAreEmpty() {
		TripCdo tripCdo = createValidTripCdo();
		tripCdo.getQuiz().setOptions(List.of());

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	private TripCdo createValidTripCdo() {
		return new TripCdo(
			"2026-04-16",
			"ryu",
			"봄 나들이",
			"logo-file-1",
			"map-file-1",
			null,
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
						new OptionCdo("정답", true))));
	}

	private void stubValidFileAssets() {
		when(fileAssetStore.findById("logo-file-1")).thenReturn(fileAsset("logo-file-1", FileType.LOGO));
		when(fileAssetStore.findById("map-file-1")).thenReturn(fileAsset("map-file-1", FileType.MAP));
		when(fileAssetStore.findById("map-file-2")).thenReturn(fileAsset("map-file-2", FileType.MAP));
	}

	private FileAsset fileAsset(String fileId, FileType fileType) {
		FileAsset fileAsset = new FileAsset(fileType, fileId + ".png", fileId + "-stored.png", "image/png", 10L);
		fileAsset.setId(fileId);
		return fileAsset;
	}
}
