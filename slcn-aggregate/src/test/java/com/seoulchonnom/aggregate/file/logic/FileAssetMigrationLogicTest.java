package com.seoulchonnom.aggregate.file.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;

class FileAssetMigrationLogicTest {
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final ObjectStorage objectStorage = mock(ObjectStorage.class);
	private final FileAssetMigrationLogic migrationLogic =
		new FileAssetMigrationLogic(fileAssetStore, objectStorage);

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(migrationLogic, "legacyDirectory", tempDir.toString());
		ReflectionTestUtils.setField(migrationLogic, "batchSize", 2);
	}

	@Test
	void migrateLegacyFiles_shouldUploadOriginalAndVariantsUnderTheirNewKeys() throws Exception {
		Path travel = Files.createDirectories(tempDir.resolve("travel"));
		Path original = Files.write(travel.resolve("stored.png"), new byte[] {1});
		Path variant = Files.write(travel.resolve("stored_home-thumb.webp"), new byte[] {2});
		FileAsset asset = new FileAsset(FileType.TRAVEL, "cover.png", "stored.png", "image/png", 1L);
		asset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(asset));

		var report = migrationLogic.migrateLegacyFiles();

		verify(objectStorage).put("originals/travel/stored.png", original, "image/png");
		verify(objectStorage).put("derived/travel/stored_home-thumb.webp", variant, "image/webp");
		assertThat(report.uploaded()).isEqualTo(2);
		assertThat(report.failed()).isZero();
	}

	@Test
	void migrateLegacyFiles_shouldSkipObjectsThatAreAlreadyUploadedSoRerunsAreSafe() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored.png"), new byte[] {1});
		FileAsset asset = new FileAsset(FileType.TRAVEL, "cover.png", "stored.png", "image/png", 1L);
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(asset));
		when(objectStorage.exists("originals/travel/stored.png")).thenReturn(true);

		var report = migrationLogic.migrateLegacyFiles();

		verify(objectStorage, never()).put(anyString(), any(Path.class), anyString());
		assertThat(report.skipped()).isEqualTo(1);
	}

	@Test
	void migrateLegacyFiles_shouldCountMissingLegacyFilesInsteadOfFailing() {
		FileAsset asset = new FileAsset(FileType.TRAVEL, "cover.png", "gone.png", "image/png", 1L);
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(asset));

		var report = migrationLogic.migrateLegacyFiles();

		assertThat(report.missing()).isEqualTo(1);
		assertThat(report.uploaded()).isZero();
	}

	@Test
	void migrateLegacyFiles_shouldKeepGoingWhenOneUploadFails() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/a.png"), new byte[] {1});
		Files.write(tempDir.resolve("travel/b.png"), new byte[] {2});
		FileAsset first = new FileAsset(FileType.TRAVEL, "a.png", "a.png", "image/png", 1L);
		FileAsset second = new FileAsset(FileType.TRAVEL, "b.png", "b.png", "image/png", 1L);
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(first, second));
		doThrow(new IOException("boom")).when(objectStorage)
			.put(eq("originals/travel/a.png"), any(Path.class), anyString());

		var report = migrationLogic.migrateLegacyFiles();

		assertThat(report.failed()).isEqualTo(1);
		assertThat(report.uploaded()).isEqualTo(1);
	}

	@Test
	void migrateLegacyFiles_shouldWalkEveryPageUntilTheStoreRunsOut() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/a.png"), new byte[] {1});
		Files.write(tempDir.resolve("travel/b.png"), new byte[] {2});
		Files.write(tempDir.resolve("travel/c.png"), new byte[] {3});
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(
			new FileAsset(FileType.TRAVEL, "a.png", "a.png", "image/png", 1L),
			new FileAsset(FileType.TRAVEL, "b.png", "b.png", "image/png", 1L)));
		when(fileAssetStore.findPage(1, 2)).thenReturn(List.of(
			new FileAsset(FileType.TRAVEL, "c.png", "c.png", "image/png", 1L)));

		var report = migrationLogic.migrateLegacyFiles();

		assertThat(report.uploaded()).isEqualTo(3);
		verify(fileAssetStore).findPage(0, 2);
		verify(fileAssetStore).findPage(1, 2);
	}
}
