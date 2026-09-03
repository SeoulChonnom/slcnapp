package com.seoulchonnom.aggregate.file.logic;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
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

	@Value("${slcn.upload.path}")
	private String directory;

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
			FileUtils.ImageProfile profile = fileUtils.writeVariants(fileAsset, staged.originalPath());

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

	public ImageFileRdo getImageFile(String type, String filename) {
		return readImageFile(type, filename, ORIGINAL_VARIANT_TAG, null, filename);
	}

	public ImageFileRdo getImageFileById(String fileId) {
		return getImageFileById(fileId, null);
	}

	/**
	 * 요청한 파생본이 없거나 아직 생성되지 않았으면 원본을 그대로 응답한다.
	 * 홈 화면이 파생본 하나 때문에 표지를 통째로 잃는 편보다 원본을 받는 편이 낫다.
	 */
	public ImageFileRdo getImageFileById(String fileId, ImageVariant variant) {
		FileAsset fileAsset = fileAssetStore.findById(fileId);
		String type = fileAsset.getType().getValue();

		Optional<FileVariant> fileVariant = fileAsset.findVariant(variant);
		if (fileVariant.isPresent()) {
			FileVariant resolved = fileVariant.get();
			if (fileUtils.existsFileRef(type, resolved.getFilename())) {
				return readImageFile(type, resolved.getFilename(), resolved.getVariant(), resolved.getMimeType(),
					fileAsset.downloadFilename(resolved));
			}
			log.warn("Variant recorded but missing on disk, serving original. fileId={}, variant={}",
				fileId, resolved.getVariant());
		}

		return readImageFile(type, fileAsset.getStoredFilename(), ORIGINAL_VARIANT_TAG, null,
			fileAsset.downloadFilename(null));
	}

	private ImageFileRdo readImageFile(String type, String filename, String variantTag, String mimeType,
		String downloadFilename) {
		fileUtils.isValidFileRef(type, filename);

		try {
			Path filePath = Paths.get(directory).resolve(type).resolve(filename).normalize();
			return ImageFileRdo.builder()
				.image(Files.readAllBytes(filePath))
				.mimeType(mimeType != null ? mimeType : Files.probeContentType(filePath))
				.variant(variantTag)
				.downloadFilename(downloadFilename)
				.build();
		} catch (IOException e) {
			throw new FilePathInvalidException();
		}
	}
}
