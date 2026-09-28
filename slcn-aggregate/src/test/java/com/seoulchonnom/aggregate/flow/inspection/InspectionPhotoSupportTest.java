package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionFileException;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;

class InspectionPhotoSupportTest {
	private static final String VISIT_ID = "INSPECTION_VISIT-0001";

	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final InspectionPhotoSupport inspectionPhotoSupport = new InspectionPhotoSupport(fileBoxStore,
		fileAssetStore, new FileBoxMapper());

	private static FileBoxItem item(String id, FileBoxTargetType targetType, String targetId, String fileAssetId,
		FileBoxItemRole role, int sortOrder) {
		return FileBoxItem.builder()
			.id(id)
			.targetType(targetType)
			.targetId(targetId)
			.fileAssetId(fileAssetId)
			.role(role)
			.sortOrder(sortOrder)
			.build();
	}

	private static FileBoxItemUdo udo(String id, String fileAssetId, FileBoxItemRole role, Integer sortOrder) {
		FileBoxItemUdo udo = new FileBoxItemUdo();
		udo.setId(id);
		udo.setFileAssetId(fileAssetId);
		udo.setRole(role);
		udo.setSortOrder(sortOrder);
		return udo;
	}

	private void existing(FileBoxItem... items) {
		FileBox fileBox = FileBox.builder()
			.ownerType(FileBoxOwnerType.INSPECTION_VISIT)
			.ownerId(VISIT_ID)
			.items(new ArrayList<>(List.of(items)))
			.build();
		when(fileBoxStore.findOptionalByOwner(FileBoxOwnerType.INSPECTION_VISIT, VISIT_ID))
			.thenReturn(Optional.of(fileBox));
	}

	private void inspectionAsset() {
		FileAsset asset = new FileAsset();
		asset.setType(FileType.INSPECTION);
		when(fileAssetStore.findById(anyString())).thenReturn(asset);
	}

	@SuppressWarnings("unchecked")
	private List<FileBoxItem> synced() {
		ArgumentCaptor<List<FileBoxItem>> captor = ArgumentCaptor.forClass(List.class);
		verify(fileBoxStore).syncItems(eq(FileBoxOwnerType.INSPECTION_VISIT), eq(VISIT_ID), captor.capture());
		return captor.getValue();
	}

	@Test
	void syncVisitPhotos_shouldKeepPropertyPhotosUntouched() {
		existing(
			item("v1", FileBoxTargetType.INSPECTION_VISIT, null, "file-1", FileBoxItemRole.GALLERY, 1),
			item("p1", FileBoxTargetType.VIEWED_PROPERTY, "prop-1", "file-2", FileBoxItemRole.COVER, 1));
		inspectionAsset();

		inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(udo(null, "file-9", FileBoxItemRole.GALLERY, null)));

		assertThat(synced()).extracting(FileBoxItem::getFileAssetId).containsExactly("file-2", "file-9");
	}

	@Test
	void syncPropertyPhotos_shouldReplaceOnlyItsOwnGroup() {
		existing(
			item("v1", FileBoxTargetType.INSPECTION_VISIT, null, "file-1", FileBoxItemRole.GALLERY, 1),
			item("p1", FileBoxTargetType.VIEWED_PROPERTY, "prop-1", "file-2", FileBoxItemRole.GALLERY, 1),
			item("p2", FileBoxTargetType.VIEWED_PROPERTY, "prop-2", "file-3", FileBoxItemRole.GALLERY, 1));
		inspectionAsset();

		inspectionPhotoSupport.syncPropertyPhotos(VISIT_ID, "prop-1",
			List.of(udo(null, "file-9", FileBoxItemRole.GALLERY, null)));

		assertThat(synced()).extracting(FileBoxItem::getFileAssetId)
			.containsExactly("file-1", "file-3", "file-9");
	}

	@Test
	void syncVisitPhotos_shouldRejectRawAttachment() {
		existing();
		inspectionAsset();
		FileBoxItemUdo withRaw = udo(null, "file-9", FileBoxItemRole.GALLERY, null);
		withRaw.setRawFileAssetId("raw-1");

		assertThatThrownBy(() -> inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(withRaw)))
			.isInstanceOf(InvalidInspectionFileException.class)
			.hasMessage("임장 사진에는 RAW 파일을 첨부할 수 없습니다.");
	}

	@Test
	void syncVisitPhotos_shouldSetTargetFromPathNotFromRequest() {
		existing();
		inspectionAsset();
		FileBoxItemUdo spoofed = udo(null, "file-9", FileBoxItemRole.GALLERY, null);
		spoofed.setTargetType(FileBoxTargetType.VIEWED_PROPERTY);
		spoofed.setTargetId("prop-1");

		inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(spoofed));

		assertThat(synced().get(0).getTargetType()).isEqualTo(FileBoxTargetType.INSPECTION_VISIT);
		assertThat(synced().get(0).getTargetId()).isNull();
	}

	@Test
	void syncVisitPhotos_shouldKeepExistingSortOrderForKnownItems() {
		existing(item("v1", FileBoxTargetType.INSPECTION_VISIT, null, "file-1", FileBoxItemRole.GALLERY, 7));
		inspectionAsset();

		// 정렬 전용 엔드포인트로 맞춘 7이, 캐시된 files 배열(sortOrder 0)에 덮이면 안 된다
		inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(udo("v1", "file-1", FileBoxItemRole.GALLERY, 0)));

		assertThat(synced()).singleElement()
			.extracting(FileBoxItem::getSortOrder).isEqualTo(7);
	}

	@Test
	void syncVisitPhotos_shouldNumberNewItemsAfterTheGroupTail() {
		existing(item("v1", FileBoxTargetType.INSPECTION_VISIT, null, "file-1", FileBoxItemRole.GALLERY, 7));
		inspectionAsset();

		inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(
			udo("v1", "file-1", FileBoxItemRole.GALLERY, 0),
			udo(null, "file-2", FileBoxItemRole.GALLERY, 0)));

		assertThat(synced()).extracting(FileBoxItem::getSortOrder).containsExactly(7, 8);
	}

	@Test
	void syncVisitPhotos_shouldRejectNonInspectionFileType() {
		existing();
		FileAsset asset = new FileAsset();
		asset.setType(FileType.TRAVEL);
		when(fileAssetStore.findById(anyString())).thenReturn(asset);

		assertThatThrownBy(() -> inspectionPhotoSupport.syncVisitPhotos(VISIT_ID,
			List.of(udo(null, "file-1", FileBoxItemRole.GALLERY, null))))
			.isInstanceOf(InvalidInspectionFileException.class);
	}

	@Test
	void syncVisitPhotos_shouldRejectSecondCover() {
		existing();
		inspectionAsset();

		assertThatThrownBy(() -> inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(
			udo(null, "file-1", FileBoxItemRole.COVER, null),
			udo(null, "file-2", FileBoxItemRole.COVER, null))))
			.isInstanceOf(InvalidInspectionFileException.class);
	}

	@Test
	void syncVisitPhotos_shouldAllowNoCoverAtAll() {
		existing();
		inspectionAsset();

		inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(udo(null, "file-1", FileBoxItemRole.GALLERY, null)));

		assertThat(synced()).hasSize(1);
	}

	@Test
	void syncVisitPhotos_shouldRejectDuplicatedAssetInSameGroup() {
		existing();
		inspectionAsset();

		assertThatThrownBy(() -> inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, List.of(
			udo(null, "file-1", FileBoxItemRole.GALLERY, null),
			udo(null, "file-1", FileBoxItemRole.GALLERY, null))))
			.isInstanceOf(InvalidInspectionFileException.class);
	}

	@Test
	void syncVisitPhotos_shouldRejectUnknownItemId() {
		existing();
		inspectionAsset();

		assertThatThrownBy(() -> inspectionPhotoSupport.syncVisitPhotos(VISIT_ID,
			List.of(udo("not-mine", "file-1", FileBoxItemRole.GALLERY, null))))
			.isInstanceOf(InvalidInspectionFileException.class);
	}

	@Test
	void syncVisitPhotos_shouldDoNothingWhenFilesOmitted() {
		inspectionPhotoSupport.syncVisitPhotos(VISIT_ID, null);

		verifyNoInteractions(fileBoxStore);
	}

	@Test
	void removePropertyPhotos_shouldDropOnlyThatProperty() {
		existing(
			item("v1", FileBoxTargetType.INSPECTION_VISIT, null, "file-1", FileBoxItemRole.GALLERY, 1),
			item("p1", FileBoxTargetType.VIEWED_PROPERTY, "prop-1", "file-2", FileBoxItemRole.GALLERY, 1),
			item("p2", FileBoxTargetType.VIEWED_PROPERTY, "prop-2", "file-3", FileBoxItemRole.GALLERY, 1));

		inspectionPhotoSupport.removePropertyPhotos(VISIT_ID, "prop-1");

		assertThat(synced()).extracting(FileBoxItem::getFileAssetId).containsExactly("file-1", "file-3");
	}
}
