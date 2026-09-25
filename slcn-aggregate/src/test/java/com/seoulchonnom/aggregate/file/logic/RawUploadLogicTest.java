package com.seoulchonnom.aggregate.file.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FileExtException;
import com.seoulchonnom.aggregate.file.exception.FileSizeException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
import com.seoulchonnom.aggregate.file.exception.PresignedUrlNotSupportedException;
import com.seoulchonnom.aggregate.file.exception.RawUploadInUseException;
import com.seoulchonnom.aggregate.file.storage.MultipartUploadStorage;
import com.seoulchonnom.aggregate.file.storage.MultipartUploadStorage.UploadedPart;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadCompleteCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadPartCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionRdo;

class RawUploadLogicTest {
	private static final long PART_SIZE = 16L * 1024 * 1024;
	private static final long RAW_SIZE = 90L * 1024 * 1024;
	private static final String STORED = "72d768d4-2b05-48f9-bee8-fee3b52e909f.raf";
	private static final String KEY = "originals/travel/" + STORED;
	private static final byte[] RAF_MAGIC = "FUJIFILMCCD-RAW ".getBytes(StandardCharsets.US_ASCII);

	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final MultipartUploadStorage storage = mock(MultipartUploadStorage.class);
	private RawUploadLogic logic;

	@BeforeEach
	void setUp() {
		logic = newLogic(Optional.of(storage));
	}

	@Test
	void createSession_shouldSavePendingRawAndPresignEveryPart() throws Exception {
		when(storage.create(anyString(), eq("image/x-fujifilm-raf"))).thenReturn("upload-1");
		when(fileAssetStore.save(any(FileAsset.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
		when(storage.presignPart(anyString(), eq("upload-1"), anyInt(), any(Duration.class)))
			.thenAnswer(invocation -> "https://r2.example/part/" + invocation.getArgument(2));

		RawUploadSessionRdo session = logic.createSession(new RawUploadSessionCdo("travel", "DSCF1234.RAF", RAW_SIZE));

		assertThat(session.getFileId()).isEqualTo("raw-1");
		assertThat(session.getUploadId()).isEqualTo("upload-1");
		assertThat(session.getPartSize()).isEqualTo(PART_SIZE);
		// 90 MB / 16 MB → 5개는 꽉 차고 마지막 1개는 10 MB
		assertThat(session.getParts()).extracting("partNumber").containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(session.getParts().get(5).getUrl()).isEqualTo("https://r2.example/part/6");
		assertThat(session.getExpiresAt()).isNotNull();

		FileAsset saved = captureSaved();
		assertThat(saved.getKind()).isEqualTo(FileKind.RAW);
		assertThat(saved.getStatus()).isEqualTo(FileStatus.PENDING);
		assertThat(saved.getType()).isEqualTo(FileType.TRAVEL);
		assertThat(saved.getUploadId()).isEqualTo("upload-1");
		assertThat(saved.getOriginalFilename()).isEqualTo("DSCF1234.RAF");
		assertThat(saved.getStoredFilename()).endsWith(".raf");
		assertThat(saved.getSize()).isEqualTo(RAW_SIZE);
		verify(storage).create("originals/travel/" + saved.getStoredFilename(), "image/x-fujifilm-raf");
	}

	@Test
	void createSession_shouldRejectNonTravelType() {
		assertThatThrownBy(() -> logic.createSession(new RawUploadSessionCdo("inspection", "DSCF1234.RAF", RAW_SIZE)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("RAW 파일은 여행 사진에만 첨부할 수 있습니다.");
		verifyNoInteractions(storage);
	}

	@Test
	void createSession_shouldRejectNonRafExtension() {
		assertThatThrownBy(() -> logic.createSession(new RawUploadSessionCdo("travel", "DSCF1234.JPG", RAW_SIZE)))
			.isInstanceOf(FileExtException.class)
			.hasMessage("RAF 파일만 RAW로 올릴 수 있습니다.");
	}

	@Test
	void createSession_shouldRejectEmptyOrOversizedFile() {
		assertThatThrownBy(() -> logic.createSession(new RawUploadSessionCdo("travel", "a.raf", 0L)))
			.isInstanceOf(FileSizeException.class);
		assertThatThrownBy(() -> logic.createSession(new RawUploadSessionCdo("travel", "a.raf", 500L * 1024 * 1024 + 1)))
			.isInstanceOf(FileSizeException.class);
		verifyNoInteractions(storage);
	}

	@Test
	void createSession_shouldAbortUploadWhenAssetCannotBeSaved() throws Exception {
		when(storage.create(anyString(), anyString())).thenReturn("upload-1");
		when(fileAssetStore.save(any(FileAsset.class))).thenThrow(new IllegalStateException("mongo down"));

		assertThatThrownBy(() -> logic.createSession(new RawUploadSessionCdo("travel", "a.raf", RAW_SIZE)))
			.isInstanceOf(IllegalStateException.class);
		verify(storage).abort(anyString(), eq("upload-1"));
	}

	@Test
	void everyEntryPoint_shouldReturn501WhenStorageCannotPresign() {
		RawUploadLogic local = newLogic(Optional.empty());

		assertThatThrownBy(() -> local.createSession(new RawUploadSessionCdo("travel", "a.raf", RAW_SIZE)))
			.isInstanceOf(PresignedUrlNotSupportedException.class);
		assertThatThrownBy(() -> local.complete("raw-1", completeCdo("upload-1")))
			.isInstanceOf(PresignedUrlNotSupportedException.class);
		assertThatThrownBy(() -> local.delete("raw-1"))
			.isInstanceOf(PresignedUrlNotSupportedException.class);
	}

	@Test
	void complete_shouldMarkReadyWhenSizeAndMagicMatch() throws Exception {
		FileAsset pending = pendingRaw();
		when(fileAssetStore.findById("raw-1")).thenReturn(pending);
		when(storage.headSize(KEY)).thenReturn(RAW_SIZE);
		when(storage.readRange(KEY, 0, 15)).thenReturn(RAF_MAGIC);
		when(fileAssetStore.save(pending)).thenReturn(pending);

		FileAsset completed = logic.complete("raw-1", completeCdo("upload-1"));

		assertThat(completed.getStatus()).isEqualTo(FileStatus.READY);
		assertThat(completed.getUploadId()).isNull();
		verify(storage).complete(KEY, "upload-1", List.of(new UploadedPart(1, "\"etag-1\"")));
	}

	@Test
	void complete_shouldRejectMismatchedUploadId() throws Exception {
		when(fileAssetStore.findById("raw-1")).thenReturn(pendingRaw());

		assertThatThrownBy(() -> logic.complete("raw-1", completeCdo("other-upload")))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("업로드 세션이 일치하지 않습니다.");
		verify(storage, never()).complete(anyString(), anyString(), anyList());
	}

	@Test
	void complete_shouldDeleteObjectAndAssetWhenMagicDoesNotMatch() throws Exception {
		when(fileAssetStore.findById("raw-1")).thenReturn(pendingRaw());
		when(storage.headSize(KEY)).thenReturn(RAW_SIZE);
		when(storage.readRange(KEY, 0, 15)).thenReturn("NOT-A-RAF-FILE!!".getBytes(StandardCharsets.US_ASCII));

		assertThatThrownBy(() -> logic.complete("raw-1", completeCdo("upload-1")))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("RAW 파일 내용이 올바르지 않습니다. 다시 올려 주세요.");
		verify(storage).delete(KEY);
		verify(fileAssetStore).deleteById("raw-1");
	}

	@Test
	void complete_shouldDeleteObjectAndAssetWhenSizeDiffersFromDeclared() throws Exception {
		when(fileAssetStore.findById("raw-1")).thenReturn(pendingRaw());
		when(storage.headSize(KEY)).thenReturn(RAW_SIZE - 1);

		assertThatThrownBy(() -> logic.complete("raw-1", completeCdo("upload-1")))
			.isInstanceOf(BadRequestException.class);
		verify(storage).delete(KEY);
		verify(fileAssetStore).deleteById("raw-1");
		verify(storage, never()).readRange(anyString(), anyLong(), anyLong());
	}

	@Test
	void complete_shouldReturnReadyAssetUnchangedWhenCalledAgain() throws Exception {
		FileAsset ready = pendingRaw();
		ready.markUploadCompleted();
		when(fileAssetStore.findById("raw-1")).thenReturn(ready);

		FileAsset result = logic.complete("raw-1", completeCdo("whatever"));

		assertThat(result).isSameAs(ready);
		verifyNoInteractions(storage);
	}

	@Test
	void complete_shouldVerifyWhenEarlierCompletionSucceededButResponseWasLost() throws Exception {
		FileAsset pending = pendingRaw();
		when(fileAssetStore.findById("raw-1")).thenReturn(pending);
		doThrow(new IOException("NoSuchUpload")).when(storage).complete(anyString(), anyString(), anyList());
		when(storage.headSize(KEY)).thenReturn(RAW_SIZE);
		when(storage.readRange(KEY, 0, 15)).thenReturn(RAF_MAGIC);
		when(fileAssetStore.save(pending)).thenReturn(pending);

		assertThat(logic.complete("raw-1", completeCdo("upload-1")).getStatus()).isEqualTo(FileStatus.READY);
	}

	@Test
	void complete_shouldKeepAssetWhenStorageFailsTransiently() throws Exception {
		when(fileAssetStore.findById("raw-1")).thenReturn(pendingRaw());
		doThrow(new IOException("timeout")).when(storage).complete(anyString(), anyString(), anyList());
		when(storage.headSize(KEY)).thenThrow(new IOException("not found"));

		assertThatThrownBy(() -> logic.complete("raw-1", completeCdo("upload-1")))
			.isInstanceOf(FileUploadException.class);
		verify(fileAssetStore, never()).deleteById(anyString());
		verify(storage, never()).delete(anyString());
	}

	@Test
	void complete_shouldRejectDuplicatedOrMissingParts() throws Exception {
		when(fileAssetStore.findById("raw-1")).thenReturn(pendingRaw());
		RawUploadCompleteCdo duplicated = new RawUploadCompleteCdo("upload-1", List.of(
			new RawUploadPartCdo(1, "\"a\""), new RawUploadPartCdo(1, "\"b\"")));

		assertThatThrownBy(() -> logic.complete("raw-1", duplicated))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("업로드 파트 정보가 올바르지 않습니다.");
		assertThatThrownBy(() -> logic.complete("raw-1", new RawUploadCompleteCdo("upload-1", List.of())))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void delete_shouldAbortPendingUploadWithStoredUploadId() throws Exception {
		when(fileAssetStore.findById("raw-1")).thenReturn(pendingRaw());

		logic.delete("raw-1");

		verify(storage).abort(KEY, "upload-1");
		verify(fileAssetStore).deleteById("raw-1");
		verify(storage, never()).delete(anyString());
	}

	@Test
	void delete_shouldRemoveReadyRawThatNoTravelUses() throws Exception {
		FileAsset ready = pendingRaw();
		ready.markUploadCompleted();
		when(fileAssetStore.findById("raw-1")).thenReturn(ready);
		when(fileBoxStore.isRawFileLinked("raw-1")).thenReturn(false);

		logic.delete("raw-1");

		verify(storage).delete(KEY);
		verify(fileAssetStore).deleteById("raw-1");
	}

	@Test
	void delete_shouldRefuseReadyRawLinkedToTravel() throws Exception {
		FileAsset ready = pendingRaw();
		ready.markUploadCompleted();
		when(fileAssetStore.findById("raw-1")).thenReturn(ready);
		when(fileBoxStore.isRawFileLinked("raw-1")).thenReturn(true);

		assertThatThrownBy(() -> logic.delete("raw-1")).isInstanceOf(RawUploadInUseException.class);
		verify(storage, never()).delete(anyString());
		verify(fileAssetStore, never()).deleteById(anyString());
	}

	@Test
	void delete_shouldRefuseImageAssets() {
		FileAsset image = new FileAsset(FileType.TRAVEL, "a.jpg", "72d768d4-2b05-48f9-bee8-fee3b52e909f.jpg",
			"image/jpeg", 1L);
		when(fileAssetStore.findById("image-1")).thenReturn(image);

		assertThatThrownBy(() -> logic.delete("image-1"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("RAW 업로드 자산이 아닙니다.");
		verifyNoInteractions(storage);
	}

	private RawUploadLogic newLogic(Optional<MultipartUploadStorage> multipartUploadStorage) {
		RawUploadLogic rawUploadLogic = new RawUploadLogic(fileAssetStore, fileBoxStore, multipartUploadStorage);
		ReflectionTestUtils.setField(rawUploadLogic, "partSize", PART_SIZE);
		ReflectionTestUtils.setField(rawUploadLogic, "sessionTtlSeconds", 1800L);
		return rawUploadLogic;
	}

	private FileAsset pendingRaw() {
		FileAsset fileAsset = FileAsset.pendingRaw("DSCF1234.RAF", STORED, "image/x-fujifilm-raf", RAW_SIZE, "upload-1");
		fileAsset.setId("raw-1");
		return fileAsset;
	}

	private static RawUploadCompleteCdo completeCdo(String uploadId) {
		return new RawUploadCompleteCdo(uploadId, List.of(new RawUploadPartCdo(1, "\"etag-1\"")));
	}

	private static FileAsset withId(FileAsset fileAsset) {
		fileAsset.setId("raw-1");
		return fileAsset;
	}

	private FileAsset captureSaved() {
		ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
		verify(fileAssetStore).save(captor.capture());
		return captor.getValue();
	}
}
