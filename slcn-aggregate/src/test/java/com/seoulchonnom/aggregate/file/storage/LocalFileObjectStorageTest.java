package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileObjectStorageTest {
	@TempDir
	Path tempDir;

	private LocalFileObjectStorage storage;

	@BeforeEach
	void setUp() {
		storage = new LocalFileObjectStorage(tempDir);
	}

	@Test
	void put_shouldCreateMissingDirectoriesAndCopyTheSource() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {1, 2, 3});

		storage.put("originals/travel/a.png", source, "image/png");

		assertThat(tempDir.resolve("originals/travel/a.png")).exists();
		assertThat(Files.readAllBytes(tempDir.resolve("originals/travel/a.png")))
			.containsExactly(1, 2, 3);
	}

	@Test
	void put_shouldOverwriteAnExistingObjectSoRetriesAreSafe() throws Exception {
		Path first = Files.write(tempDir.resolve("first.bin"), new byte[] {1});
		Path second = Files.write(tempDir.resolve("second.bin"), new byte[] {2});

		storage.put("originals/travel/a.png", first, "image/png");
		storage.put("originals/travel/a.png", second, "image/png");

		assertThat(storage.getBytes("originals/travel/a.png")).containsExactly(2);
	}

	@Test
	void getBytes_shouldReturnStoredBytes() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {9, 9});
		storage.put("derived/travel/a_home-thumb.webp", source, "image/webp");

		assertThat(storage.getBytes("derived/travel/a_home-thumb.webp")).containsExactly(9, 9);
	}

	@Test
	void getBytes_shouldThrowWhenObjectIsMissing() {
		assertThatThrownBy(() -> storage.getBytes("originals/travel/absent.png"))
			.isInstanceOf(IOException.class);
	}

	@Test
	void exists_shouldReflectObjectPresence() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {1});
		storage.put("originals/travel/a.png", source, "image/png");

		assertThat(storage.exists("originals/travel/a.png")).isTrue();
		assertThat(storage.exists("originals/travel/b.png")).isFalse();
	}

	@Test
	void presignedGetUrl_shouldBeEmptySoCallersServeBytesThemselves() {
		assertThat(storage.presignedGetUrl("originals/travel/a.png", Duration.ofMinutes(5), null))
			.isEmpty();
	}

	@Test
	void put_shouldRejectKeysThatEscapeTheBaseDirectory() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {1});

		assertThatThrownBy(() -> storage.put("../escaped.png", source, "image/png"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
