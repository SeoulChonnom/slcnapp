package com.seoulchonnom.aggregate.travel.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
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
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.vo.TravelDay;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;
import com.seoulchonnom.spec.travel.mapper.TravelMapper;

/**
 * 여행 사진과 RAW 첨부 연결 규칙. 보기용 사진 하나에 RAW 하나가 1:1로 붙는다.
 */
class TravelLogicRawAttachmentTest {
	private final TravelStore travelStore = mock(TravelStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final TravelMapper travelMapper = mock(TravelMapper.class);
	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final FileBoxMapper fileBoxMapper = new FileBoxMapper();
	private final TravelLogic travelLogic = new TravelLogic(travelStore, idGenerator, travelMapper, fileBoxStore,
		fileAssetStore, fileBoxMapper);

	@BeforeEach
	void setUp() {
		when(idGenerator.nextDomainId("TRAVEL")).thenReturn("TRAVEL-0001");
		when(travelStore.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelMapper.toTravelDetailRdo(any(), anyList())).thenReturn(new TravelDetailRdo());
		when(fileBoxStore.findOptionalByOwner(eq(FileBoxOwnerType.TRAVEL), anyString())).thenReturn(Optional.empty());
		when(fileBoxStore.createForOwner(eq(FileBoxOwnerType.TRAVEL), anyString()))
			.thenAnswer(invocation -> fileBox(invocation.getArgument(1), List.of()));
		stubImage("photo-1");
		stubImage("photo-2");
		stubRaw("raw-1", true);
		stubRaw("raw-2", true);
	}

	@Test
	void registerTravel_shouldKeepRawLinkWhenSamePhotoIsCoverAndGallery() {
		travelLogic.registerTravel(travelWith(
			item("photo-1", "raw-1", FileBoxItemRole.COVER),
			item("photo-1", "raw-1", FileBoxItemRole.GALLERY)));

		verify(fileBoxStore).syncItems(eq(FileBoxOwnerType.TRAVEL), eq("TRAVEL-0001"), argThat(items ->
			items.size() == 2 && items.stream().allMatch(synced -> "raw-1".equals(synced.getRawFileAssetId()))));
	}

	@Test
	void registerTravel_shouldRejectRawAssetUsedAsViewablePhoto() {
		assertRejected("RAW 파일은 보기용 사진으로 연결할 수 없습니다.",
			item("raw-1", null, FileBoxItemRole.COVER));
	}

	@Test
	void registerTravel_shouldRejectRawLinkPointingAtImageOrMissingAsset() {
		assertRejected("연결할 RAW 파일이 올바르지 않습니다.", item("photo-1", "photo-2", FileBoxItemRole.COVER));
		assertRejected("연결할 RAW 파일이 올바르지 않습니다.", item("photo-1", "missing", FileBoxItemRole.COVER));
	}

	@Test
	void registerTravel_shouldRejectRawThatIsStillUploading() {
		stubRaw("raw-pending", false);

		assertRejected("RAW 업로드가 아직 끝나지 않았습니다.", item("photo-1", "raw-pending", FileBoxItemRole.COVER));
	}

	@Test
	void registerTravel_shouldRejectSameRawOnDifferentPhotos() {
		assertRejected("하나의 사진에는 하나의 RAW 파일만 연결할 수 있습니다.",
			item("photo-1", "raw-1", FileBoxItemRole.COVER),
			item("photo-2", "raw-1", FileBoxItemRole.GALLERY));
	}

	@Test
	void registerTravel_shouldRejectDifferentRawsOnSamePhoto() {
		assertRejected("하나의 사진에는 하나의 RAW 파일만 연결할 수 있습니다.",
			item("photo-1", "raw-1", FileBoxItemRole.COVER),
			item("photo-1", "raw-2", FileBoxItemRole.GALLERY));
	}

	@Test
	void registerTravel_shouldTreatBlankRawIdAsNoRaw() {
		travelLogic.registerTravel(travelWith(item("photo-1", "  ", FileBoxItemRole.COVER)));

		verify(fileBoxStore).syncItems(eq(FileBoxOwnerType.TRAVEL), eq("TRAVEL-0001"), argThat(items ->
			items.get(0).getRawFileAssetId() == null));
	}

	@Test
	void modifyTravel_shouldDropRawLinkWhenItemIsResentWithoutIt() {
		FileBoxItem existing = FileBoxItem.builder()
			.id("item-cover")
			.fileAssetId("photo-1")
			.rawFileAssetId("raw-1")
			.targetType(FileBoxTargetType.TRAVEL)
			.role(FileBoxItemRole.COVER)
			.sortOrder(1)
			.build();
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1"));
		when(fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, "travel-1"))
			.thenReturn(Optional.of(fileBox("travel-1", List.of(existing))));
		TravelUdo udo = new TravelUdo("서울", "서울", "2026-06-01", "2026-06-02", null, null);
		udo.setFiles(List.of(new FileBoxItemUdo("item-cover", "photo-1", null, FileBoxTargetType.TRAVEL, null,
			FileBoxItemRole.COVER, null, 1)));

		travelLogic.modifyTravel("travel-1", udo);

		verify(fileBoxStore).syncItems(eq(FileBoxOwnerType.TRAVEL), eq("travel-1"), argThat(items ->
			items.size() == 1 && items.get(0).getRawFileAssetId() == null));
	}

	@Test
	void travelDetail_shouldFillRawFileForDownloadButtonAndSkipMissingRaw() {
		FileBoxItem withRaw = savedItem("item-1", "photo-1", "raw-1", FileBoxItemRole.COVER);
		FileBoxItem withDeletedRaw = savedItem("item-2", "photo-2", "raw-gone", FileBoxItemRole.GALLERY);
		when(fileBoxStore.findOptionalByOwner(FileBoxOwnerType.TRAVEL, "TRAVEL-0001"))
			.thenReturn(Optional.of(fileBox("TRAVEL-0001", List.of(withRaw, withDeletedRaw))));

		travelLogic.registerTravel(travelWith(item("photo-1", "raw-1", FileBoxItemRole.COVER)));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<FileBoxItemRdo>> captor = ArgumentCaptor.forClass(List.class);
		verify(travelMapper).toTravelDetailRdo(any(), captor.capture());
		FileBoxItemRdo cover = captor.getValue().stream().filter(rdo -> "item-1".equals(rdo.getId())).findFirst()
			.orElseThrow();
		FileBoxItemRdo gallery = captor.getValue().stream().filter(rdo -> "item-2".equals(rdo.getId())).findFirst()
			.orElseThrow();
		assertThat(cover.getRawFileAssetId()).isEqualTo("raw-1");
		assertThat(cover.getRawFile().getOriginalFilename()).isEqualTo("raw-1.RAF");
		assertThat(cover.getRawFile().getSize()).isEqualTo(83886080L);
		assertThat(gallery.getRawFileAssetId()).isEqualTo("raw-gone");
		assertThat(gallery.getRawFile()).isNull();
	}

	private void assertRejected(String message, FileBoxItemCdo... items) {
		assertThatThrownBy(() -> travelLogic.registerTravel(travelWith(items)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage(message);
	}

	private TravelCdo travelWith(FileBoxItemCdo... items) {
		TravelCdo cdo = new TravelCdo("서울", "서울", "2026-06-01", "2026-06-02", List.of());
		cdo.setFiles(List.of(items));
		return cdo;
	}

	private static FileBoxItemCdo item(String fileAssetId, String rawFileAssetId, FileBoxItemRole role) {
		return new FileBoxItemCdo(fileAssetId, rawFileAssetId, FileBoxTargetType.TRAVEL, null, role, null, null);
	}

	private static FileBoxItem savedItem(String id, String fileAssetId, String rawFileAssetId, FileBoxItemRole role) {
		return FileBoxItem.builder()
			.id(id)
			.fileAssetId(fileAssetId)
			.rawFileAssetId(rawFileAssetId)
			.targetType(FileBoxTargetType.TRAVEL)
			.role(role)
			.sortOrder(1)
			.build();
	}

	private void stubImage(String fileId) {
		FileAsset image = new FileAsset(FileType.TRAVEL, fileId + ".jpg", fileId + "-stored.jpg", "image/jpeg", 10L);
		image.setId(fileId);
		when(fileAssetStore.findById(fileId)).thenReturn(image);
		when(fileAssetStore.findOptionalById(fileId)).thenReturn(Optional.of(image));
	}

	private void stubRaw(String fileId, boolean ready) {
		FileAsset raw = FileAsset.pendingRaw(fileId + ".RAF", fileId + "-stored.raf", "image/x-fujifilm-raf",
			83886080L, "upload-" + fileId);
		raw.setId(fileId);
		if (ready) {
			raw.markUploadCompleted();
		}
		when(fileAssetStore.findById(fileId)).thenReturn(raw);
		when(fileAssetStore.findOptionalById(fileId)).thenReturn(Optional.of(raw));
	}

	private Travel travel(String travelId) {
		Travel travel = Travel.builder()
			.title("서울")
			.region("서울")
			.startDate(LocalDate.of(2026, 6, 1))
			.endDate(LocalDate.of(2026, 6, 2))
			.days(List.of(day("2026-06-01", 1), day("2026-06-02", 2)))
			.build();
		travel.setId(travelId);
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

	private FileBox fileBox(String ownerId, List<FileBoxItem> items) {
		return FileBox.builder()
			.ownerType(FileBoxOwnerType.TRAVEL)
			.ownerId(ownerId)
			.items(items)
			.build();
	}
}
