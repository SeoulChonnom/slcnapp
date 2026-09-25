package com.seoulchonnom.aggregate.file.logic;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
import com.seoulchonnom.aggregate.file.storage.MimeTypes;
import com.seoulchonnom.aggregate.file.storage.ObjectKeys;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.file.util.FileUtils;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;
import com.seoulchonnom.spec.file.entity.vo.ImageVariant;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileLogic {
	private final FileUtils fileUtils;
	private final FileAssetStore fileAssetStore;
	private final ObjectStorage objectStorage;
	/**
	 * 파생본 생성은 축소 디코딩이라도 한 건에 수십 MB를 쓴다. 여러 장 업로드가 겹쳐도 힙 사용량이 묶이도록 동시 2건으로 제한한다.
	 * 공정 모드라 먼저 온 요청이 먼저 들어간다.
	 */
	private final Semaphore variantPermits = new Semaphore(2, true);

	@Value("${slcn.storage.presigned-ttl-seconds:300}")
	private long presignedTtlSeconds;

	/**
	 * 파생본 생성 차례를 기다리는 최대 시간. 무기한 대기하면 nginx 타임아웃에 걸려 업로드 전체가 실패하므로,
	 * 넘기면 파생본 없이 업로드를 끝낸다. 조회는 원본으로 폴백한다.
	 */
	@Value("${slcn.upload.variant-wait-seconds:60}")
	private long variantWaitSeconds;

	public FileAsset uploadFile(MultipartFile file, String type) {
		return uploadFileAsset(file, type);
	}

	public List<FileAsset> uploadFiles(List<MultipartFile> files, String type) {
		if (CollectionUtils.isEmpty(files)) {
			throw new BadRequestException("files는 필수입니다.");
		}
		return files.stream()
			.map(file -> uploadFileAsset(file, type))
			.toList();
	}

	/**
	 * 원본을 임시 디렉터리에 받아 파생본을 만든 뒤, 오브젝트 업로드가 모두 끝난 다음에 메타데이터를 저장한다.
	 * 순서를 뒤집으면 객체가 없는 메타데이터가 생겨 조회가 깨진다. 반대 방향의 고아 객체는 조회에 영향을 주지 않는다.
	 */
	public FileAsset uploadFileAsset(MultipartFile file, String type) {
		FileUtils.StagedUpload staged = null;
		try {
			staged = fileUtils.stageUpload(file, type);
			FileAsset fileAsset = staged.fileAsset();
			String assetType = fileAsset.getType().getValue();
			FileUtils.ImageProfile profile = buildProfile(fileAsset, staged.originalPath());

			objectStorage.put(ObjectKeys.original(assetType, fileAsset.getStoredFilename()),
				staged.originalPath(), fileAsset.getMimeType());
			for (FileVariant variant : profile.variants()) {
				objectStorage.put(ObjectKeys.derived(assetType, variant.getFilename()),
					staged.stagingDirectory().resolve(variant.getFilename()), variant.getMimeType());
			}

			fileAsset.setWidth(profile.width());
			fileAsset.setHeight(profile.height());
			fileAsset.setVariants(new ArrayList<>(profile.variants()));
			return fileAssetStore.save(fileAsset);
		} catch (IOException e) {
			throw new FileUploadException();
		} finally {
			if (staged != null) {
				fileUtils.deleteStaging(staged.stagingDirectory());
			}
		}
	}

	private FileUtils.ImageProfile buildProfile(FileAsset fileAsset, Path originalPath) {
		if (!acquireVariantPermit()) {
			log.warn("Variant generation skipped: waited too long for a permit. path={}", fileAsset.getPath());
			return fileUtils.readProfile(originalPath);
		}

		try {
			return fileUtils.writeVariants(fileAsset, originalPath);
		} finally {
			variantPermits.release();
		}
	}

	private boolean acquireVariantPermit() {
		try {
			return variantPermits.tryAcquire(variantWaitSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * 경로 기반 조회. 파일명만 받으므로 접미사로 원본/파생본을 가른다.
	 * RAW 첨부는 거부한다. 이 경로는 쿠키 인증과 캐시가 허용되어 img 태그에 물릴 수 있고,
	 * 그러면 수십 MB짜리 RAW가 이미지로 내려간다. RAW는 download-url로만 받는다.
	 */
	public ImageFileRdo getImageFile(String type, String filename) {
		fileUtils.isValidFileRef(type, filename);
		if (filename.toLowerCase(Locale.ROOT).endsWith("." + RAW_EXT)) {
			throw new FilePathInvalidException();
		}

		String key = ObjectKeys.of(type, filename);
		if (!ObjectKeys.isDerived(key)) {
			Optional<ImageFileRdo> redirect = presignedRdo(key, ORIGINAL_VARIANT_TAG, filename, null);
			if (redirect.isPresent()) {
				return redirect.get();
			}
		}

		return ImageFileRdo.builder()
			.image(readBytes(key))
			.mimeType(MimeTypes.ofFilename(filename))
			.variant(ORIGINAL_VARIANT_TAG)
			.downloadFilename(filename)
			.build();
	}

	public ImageFileRdo getImageFileById(String fileId) {
		return getImageFileById(fileId, null);
	}

	/**
	 * 요청한 파생본이 없거나 읽히지 않으면 원본을 응답한다.
	 * 홈 화면이 파생본 하나 때문에 표지를 통째로 잃는 편보다 원본을 받는 편이 낫다.
	 */
	public ImageFileRdo getImageFileById(String fileId, ImageVariant variant) {
		return readImageFileById(fileId, variant, false);
	}

	/**
	 * 조회와 같은 자산을 첨부 파일로 내려준다.
	 * 원본은 리다이렉트로 나가므로 Content-Disposition을 서명 URL에 실어야 하고, 그래서 조회와 진입점을 나눈다.
	 */
	public ImageFileRdo downloadImageFileById(String fileId, ImageVariant variant) {
		return readImageFileById(fileId, variant, true);
	}

	private ImageFileRdo readImageFileById(String fileId, ImageVariant variant, boolean attachment) {
		FileAsset fileAsset = fileAssetStore.findById(fileId);
		String type = fileAsset.getType().getValue();

		Optional<FileVariant> fileVariant = fileAsset.findVariant(variant);
		if (fileVariant.isPresent()) {
			FileVariant resolved = fileVariant.get();
			Optional<ImageFileRdo> derived = readDerived(type, fileAsset, resolved);
			if (derived.isPresent()) {
				return derived.get();
			}
			log.warn("Variant recorded but unreadable, serving original. fileId={}, variant={}",
				fileId, resolved.getVariant());
		}

		return readOriginal(type, fileAsset, attachment);
	}

	/**
	 * 파생본은 작고 재사용률이 높다. 서명 URL은 서명할 때마다 값이 바뀌어 캐시가 매번 빗나가므로 계속 바이트로 서빙한다.
	 * 존재 확인을 따로 하지 않는 것은 요청마다 HeadObject 왕복이 하나 더 붙기 때문이다. 읽어 보고 실패하면 원본으로 폴백한다.
	 */
	private Optional<ImageFileRdo> readDerived(String type, FileAsset fileAsset, FileVariant resolved) {
		fileUtils.isValidFileRef(type, resolved.getFilename());

		try {
			return Optional.of(ImageFileRdo.builder()
				.image(objectStorage.getBytes(ObjectKeys.derived(type, resolved.getFilename())))
				.mimeType(resolved.getMimeType())
				.variant(resolved.getVariant())
				.downloadFilename(fileAsset.downloadFilename(resolved))
				.build());
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/**
	 * 원본은 100 MB에 이를 수 있어 서버 힙에 올리지 않는다. 서명할 수 있으면 클라이언트를 저장소로 직접 보낸다.
	 */
	private ImageFileRdo readOriginal(String type, FileAsset fileAsset, boolean attachment) {
		String filename = fileAsset.getStoredFilename();
		fileUtils.isValidFileRef(type, filename);

		String key = ObjectKeys.original(type, filename);
		String downloadFilename = fileAsset.downloadFilename(null);
		Optional<ImageFileRdo> redirect = presignedRdo(key, ORIGINAL_VARIANT_TAG, downloadFilename,
			attachment ? attachmentDisposition(downloadFilename) : null);
		if (redirect.isPresent()) {
			return redirect.get();
		}

		return ImageFileRdo.builder()
			.image(readBytes(key))
			.mimeType(fileAsset.getMimeType())
			.variant(ORIGINAL_VARIANT_TAG)
			.downloadFilename(downloadFilename)
			.build();
	}

	private Optional<ImageFileRdo> presignedRdo(String key, String variantTag, String downloadFilename,
		String contentDisposition) {
		return objectStorage.presignedGetUrl(key, Duration.ofSeconds(presignedTtlSeconds), contentDisposition)
			.map(url -> ImageFileRdo.builder()
				.redirectUrl(url)
				.variant(variantTag)
				.downloadFilename(downloadFilename)
				.build());
	}

	private byte[] readBytes(String key) {
		try {
			return objectStorage.getBytes(key);
		} catch (IOException e) {
			throw new FilePathInvalidException();
		}
	}

	/**
	 * 서명 URL에 실어 보낼 Content-Disposition. 한글 파일명이 들어올 수 있으므로 RFC 5987로 인코딩한다.
	 */
	private String attachmentDisposition(String filename) {
		String safeFilename = StringUtils.hasText(filename) ? filename : "download";
		return ContentDisposition.attachment()
			.filename(safeFilename, StandardCharsets.UTF_8)
			.build()
			.toString();
	}
}
