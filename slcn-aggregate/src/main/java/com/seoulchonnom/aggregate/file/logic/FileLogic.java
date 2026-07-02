package com.seoulchonnom.aggregate.file.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.file.util.FileUtils;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

import lombok.RequiredArgsConstructor;

@Component
@Service
@RequiredArgsConstructor
public class FileLogic {
	private final FileUtils fileUtils;
	private final FileAssetStore fileAssetStore;

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

	public FileAsset uploadFileAsset(MultipartFile file, String type) {
		try {
			return fileAssetStore.save(fileUtils.saveImageAsset(file, type));
		} catch (IOException e) {
			throw new FileUploadException();
		}
	}

	public ImageFileRdo getImageFile(String type, String filename) {
		fileUtils.isValidFileRef(type, filename);

		try {
			Path filePath = Paths.get(directory).resolve(type).resolve(filename).normalize();
			return ImageFileRdo.builder()
				.image(Files.readAllBytes(filePath))
				.mimeType(Files.probeContentType(filePath))
				.build();
		} catch (IOException e) {
			throw new FilePathInvalidException();
		}
	}

	public ImageFileRdo getImageFileById(String fileId) {
		FileAsset fileAsset = fileAssetStore.findById(fileId);
		return getImageFile(fileAsset.getType().getValue(), fileAsset.getStoredFilename());
	}
}
