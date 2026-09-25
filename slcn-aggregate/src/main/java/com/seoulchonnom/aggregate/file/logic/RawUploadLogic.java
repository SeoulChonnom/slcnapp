package com.seoulchonnom.aggregate.file.logic;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FileExtException;
import com.seoulchonnom.aggregate.file.exception.FileSizeException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
import com.seoulchonnom.aggregate.file.exception.PresignedUrlNotSupportedException;
import com.seoulchonnom.aggregate.file.exception.RawUploadInUseException;
import com.seoulchonnom.aggregate.file.storage.MultipartUploadStorage;
import com.seoulchonnom.aggregate.file.storage.MultipartUploadStorage.UploadedPart;
import com.seoulchonnom.aggregate.file.storage.ObjectKeys;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadCompleteCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadPartCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadPartUrlRdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionRdo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAW 첨부의 브라우저 직접 업로드. 서버는 세션 발급과 완료 검증만 하고 RAW 바이트를 디코딩하지 않는다.
 * 완료 검증에서 읽는 바이트는 매직 확인용 앞 16바이트뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RawUploadLogic {
	private final FileAssetStore fileAssetStore;
	private final FileBoxStore fileBoxStore;
	/**
	 * 로컬 프로바이더에서는 빈이 없다. 서명 URL 없이는 직접 업로드가 불가능하므로 모든 진입점이 501로 막는다.
	 */
	private final Optional<MultipartUploadStorage> multipartUploadStorage;

	/**
	 * R2는 마지막 파트를 뺀 모든 파트 크기가 같아야 하고 5 MB 이상이어야 한다. 고정 크기로 두면 두 조건이 모두 맞는다.
	 */
	@Value("${slcn.storage.raw.part-size-bytes:16777216}")
	private long partSize;

	@Value("${slcn.storage.raw.session-ttl-seconds:1800}")
	private long sessionTtlSeconds;

	public RawUploadSessionRdo createSession(RawUploadSessionCdo cdo) {
		MultipartUploadStorage storage = requireStorage();
		validateSession(cdo);

		String storedFilename = UUID.randomUUID() + "." + RAW_EXT;
		String key = ObjectKeys.original(FileType.TRAVEL.getValue(), storedFilename);
		String uploadId = createUpload(storage, key);
		FileAsset saved = savePending(storage, key, uploadId,
			FileAsset.pendingRaw(cdo.getFilename().trim(), storedFilename, RAW_MIME_TYPE, cdo.getSize(), uploadId));

		Duration ttl = Duration.ofSeconds(sessionTtlSeconds);
		OffsetDateTime expiresAt = OffsetDateTime.now().plus(ttl);
		try {
			return new RawUploadSessionRdo(saved.getId(), uploadId, partSize, expiresAt,
				presignParts(storage, key, uploadId, cdo.getSize(), ttl));
		} catch (IOException e) {
			discardPending(storage, saved);
			throw new FileUploadException();
		}
	}

	/**
	 * 이미 READY면 같은 결과를 돌려준다. 완료 응답을 못 받은 브라우저가 다시 호출해도 안전해야 한다.
	 */
	public FileAsset complete(String fileId, RawUploadCompleteCdo cdo) {
		MultipartUploadStorage storage = requireStorage();
		FileAsset fileAsset = findRaw(fileId);
		if (!fileAsset.isPending()) {
			return fileAsset;
		}
		if (cdo == null || !StringUtils.hasText(cdo.getUploadId()) || !cdo.getUploadId().equals(fileAsset.getUploadId())) {
			throw new BadRequestException(RAW_UPLOAD_SESSION_MISMATCH_ERROR_MESSAGE);
		}

		String key = keyOf(fileAsset);
		completeUpload(storage, key, fileAsset.getUploadId(), toUploadedParts(cdo.getParts()));
		if (!hasExpectedContent(storage, key, fileAsset.getSize())) {
			deleteObjectQuietly(storage, key);
			fileAssetStore.deleteById(fileAsset.getId());
			throw new BadRequestException(RAW_UPLOAD_CONTENT_INVALID_ERROR_MESSAGE);
		}

		fileAsset.markUploadCompleted();
		return fileAssetStore.save(fileAsset);
	}

	/**
	 * 업로드 중이면 세션을 중단하고, 끝났지만 어떤 여행에도 연결되지 않았으면 객체까지 지운다.
	 * 여러 RAW 중 일부만 끝난 뒤 저장을 포기하는 흐름이 흔해서, 여기서 막으면 장당 수십 MB 고아가 남는다.
	 * 연결 확인과 삭제 사이의 경합은 락 없이 둔다. 생겨도 여행 상세의 RAW 다운로드 버튼만 사라진다.
	 */
	public void delete(String fileId) {
		MultipartUploadStorage storage = requireStorage();
		FileAsset fileAsset = findRaw(fileId);
		String key = keyOf(fileAsset);

		try {
			if (fileAsset.isPending()) {
				storage.abort(key, fileAsset.getUploadId());
			} else {
				if (fileBoxStore.isRawFileLinked(fileAsset.getId())) {
					throw new RawUploadInUseException();
				}
				storage.delete(key);
			}
		} catch (IOException e) {
			throw new FileUploadException();
		}
		fileAssetStore.deleteById(fileAsset.getId());
	}

	private MultipartUploadStorage requireStorage() {
		return multipartUploadStorage.orElseThrow(PresignedUrlNotSupportedException::new);
	}

	private void validateSession(RawUploadSessionCdo cdo) {
		if (cdo == null || !FileType.TRAVEL.getValue().equals(cdo.getType())) {
			throw new BadRequestException(RAW_UPLOAD_TYPE_ERROR_MESSAGE);
		}
		if (!StringUtils.hasText(cdo.getFilename()) || !RAW_EXT.equals(extensionOf(cdo.getFilename()))) {
			throw new FileExtException(RAW_UPLOAD_EXT_ERROR_MESSAGE);
		}
		if (cdo.getSize() == null || cdo.getSize() <= 0 || cdo.getSize() > MAX_RAW_FILE_SIZE) {
			throw new FileSizeException();
		}
	}

	private String extensionOf(String filename) {
		int pos = filename.trim().lastIndexOf('.');
		return pos < 0 ? "" : filename.trim().substring(pos + 1).toLowerCase(Locale.ROOT);
	}

	private String createUpload(MultipartUploadStorage storage, String key) {
		try {
			return storage.create(key, RAW_MIME_TYPE);
		} catch (IOException e) {
			throw new FileUploadException();
		}
	}

	/**
	 * 저장소 세션을 먼저 열고 자산을 저장한다. 자산 저장이 실패하면 열어 둔 세션을 닫아 과금되는 고아 파트를 남기지 않는다.
	 */
	private FileAsset savePending(MultipartUploadStorage storage, String key, String uploadId, FileAsset fileAsset) {
		try {
			return fileAssetStore.save(fileAsset);
		} catch (RuntimeException e) {
			abortQuietly(storage, key, uploadId);
			throw e;
		}
	}

	private List<RawUploadPartUrlRdo> presignParts(MultipartUploadStorage storage, String key, String uploadId,
		long size, Duration ttl) throws IOException {
		int partCount = (int)((size + partSize - 1) / partSize);
		List<RawUploadPartUrlRdo> parts = new ArrayList<>(partCount);
		for (int partNumber = 1; partNumber <= partCount; partNumber++) {
			parts.add(new RawUploadPartUrlRdo(partNumber, storage.presignPart(key, uploadId, partNumber, ttl)));
		}
		return parts;
	}

	private FileAsset findRaw(String fileId) {
		FileAsset fileAsset = fileAssetStore.findById(fileId);
		if (!fileAsset.isRaw()) {
			throw new BadRequestException(RAW_UPLOAD_NOT_RAW_ERROR_MESSAGE);
		}
		return fileAsset;
	}

	private String keyOf(FileAsset fileAsset) {
		return ObjectKeys.original(fileAsset.getType().getValue(), fileAsset.getStoredFilename());
	}

	private List<UploadedPart> toUploadedParts(List<RawUploadPartCdo> parts) {
		if (parts == null || parts.isEmpty()) {
			throw new BadRequestException(RAW_UPLOAD_PARTS_INVALID_ERROR_MESSAGE);
		}

		Set<Integer> partNumbers = new HashSet<>();
		List<UploadedPart> uploadedParts = new ArrayList<>(parts.size());
		for (RawUploadPartCdo part : parts) {
			if (part == null || part.getPartNumber() == null || part.getPartNumber() <= 0
				|| !StringUtils.hasText(part.getEtag()) || !partNumbers.add(part.getPartNumber())) {
				throw new BadRequestException(RAW_UPLOAD_PARTS_INVALID_ERROR_MESSAGE);
			}
			uploadedParts.add(new UploadedPart(part.getPartNumber(), part.getEtag().trim()));
		}
		return uploadedParts;
	}

	/**
	 * 완료 호출이 저장소에서는 성공했는데 응답이 유실됐다면, 재시도 때 세션이 이미 없어 실패한다.
	 * 이때 객체가 있으면 앞선 완료가 성공한 것이므로 검증 단계로 넘어간다.
	 * 실패를 곧바로 삭제로 이어가지 않는 것은 일시 오류에 사용자가 수십 MB를 다시 올리게 되기 때문이다.
	 */
	private void completeUpload(MultipartUploadStorage storage, String key, String uploadId, List<UploadedPart> parts) {
		try {
			storage.complete(key, uploadId, parts);
		} catch (IOException e) {
			if (!objectExists(storage, key)) {
				log.warn("RAW multipart completion failed. key={}", key, e);
				throw new FileUploadException();
			}
		}
	}

	private boolean objectExists(MultipartUploadStorage storage, String key) {
		try {
			storage.headSize(key);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * 크기는 세션 생성 때 선언한 값과 같아야 하고, 앞 16바이트는 RAF 매직이어야 한다.
	 * 저장소 읽기 자체가 실패한 것은 내용 불일치가 아니므로 삭제하지 않고 업로드 실패로 돌려 재시도하게 한다.
	 */
	private boolean hasExpectedContent(MultipartUploadStorage storage, String key, long expectedSize) {
		byte[] magic = RAF_MAGIC.getBytes(StandardCharsets.US_ASCII);
		try {
			if (storage.headSize(key) != expectedSize) {
				return false;
			}
			return Arrays.equals(storage.readRange(key, 0, magic.length - 1L), magic);
		} catch (IOException e) {
			log.warn("RAW content verification could not read the object. key={}", key, e);
			throw new FileUploadException();
		}
	}

	private void discardPending(MultipartUploadStorage storage, FileAsset fileAsset) {
		abortQuietly(storage, keyOf(fileAsset), fileAsset.getUploadId());
		fileAssetStore.deleteById(fileAsset.getId());
	}

	private void abortQuietly(MultipartUploadStorage storage, String key, String uploadId) {
		try {
			storage.abort(key, uploadId);
		} catch (IOException e) {
			// 버킷 라이프사이클(미완료 multipart 7일 중단)이 마지막 안전망이다.
			log.warn("Failed to abort RAW multipart upload. key={}, uploadId={}", key, uploadId, e);
		}
	}

	private void deleteObjectQuietly(MultipartUploadStorage storage, String key) {
		try {
			storage.delete(key);
		} catch (IOException e) {
			log.warn("Failed to delete rejected RAW object. key={}", key, e);
		}
	}
}
