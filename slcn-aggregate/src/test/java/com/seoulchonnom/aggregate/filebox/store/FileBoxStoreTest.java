package com.seoulchonnom.aggregate.filebox.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.filebox.store.doc.FileBoxDoc;
import com.seoulchonnom.aggregate.filebox.store.mapper.FileBoxDocMapper;
import com.seoulchonnom.aggregate.filebox.store.repository.FileBoxRepository;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;

class FileBoxStoreTest {
	private final FileBoxRepository fileBoxRepository = mock(FileBoxRepository.class);
	private final FileBoxDocMapper fileBoxDocMapper = new FileBoxDocMapper();
	private final FileBoxStore fileBoxStore = new FileBoxStore(fileBoxRepository, fileBoxDocMapper);

	@Test
	void syncItems_shouldCreateMissingOwnerAndGenerateNewItemIds() {
		when(fileBoxRepository.findByOwnerTypeAndOwnerId(FileBoxOwnerType.TRAVEL, "TRAVEL-1"))
			.thenReturn(Optional.empty());
		when(fileBoxRepository.save(any(FileBoxDoc.class))).thenAnswer(invocation -> invocation.getArgument(0));
		FileBoxItem item = FileBoxItem.builder()
			.fileAssetId("file-1")
			.targetType(FileBoxTargetType.TRAVEL)
			.role(FileBoxItemRole.COVER)
			.sortOrder(1)
			.build();

		FileBox fileBox = fileBoxStore.syncItems(FileBoxOwnerType.TRAVEL, "TRAVEL-1", List.of(item));

		assertThat(fileBox.getItems()).hasSize(1);
		assertThat(fileBox.getItems().get(0).getId()).isNotBlank();
		ArgumentCaptor<FileBoxDoc> docCaptor = ArgumentCaptor.forClass(FileBoxDoc.class);
		verify(fileBoxRepository).save(docCaptor.capture());
		assertThat(docCaptor.getValue().getOwnerId()).isEqualTo("TRAVEL-1");
	}

	@Test
	void syncItems_shouldRejectUnknownExistingItemId() {
		FileBoxDoc doc = new FileBoxDoc("box-1", FileBoxOwnerType.TRAVEL, "TRAVEL-1", List.of(), 1L, 1L);
		when(fileBoxRepository.findByOwnerTypeAndOwnerId(FileBoxOwnerType.TRAVEL, "TRAVEL-1"))
			.thenReturn(Optional.of(doc));
		FileBoxItem item = FileBoxItem.builder()
			.id("unknown")
			.fileAssetId("file-1")
			.targetType(FileBoxTargetType.TRAVEL)
			.role(FileBoxItemRole.COVER)
			.sortOrder(1)
			.build();

		assertThatThrownBy(() -> fileBoxStore.syncItems(FileBoxOwnerType.TRAVEL, "TRAVEL-1", List.of(item)))
			.hasMessage("FileBoxItem.id가 기존 owner의 item이 아닙니다.");
	}
}
