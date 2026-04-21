package com.seoulchonnom.aggregate.file.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
import com.seoulchonnom.aggregate.file.util.FileUtils;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

import lombok.RequiredArgsConstructor;

@Component
@Service
@RequiredArgsConstructor
public class FileLogic {
	private final FileUtils fileUtils;

	@Value("${upload.path}")
	private String directory;

	public String uploadFile(MultipartFile file, String path) {
		try {
			return fileUtils.saveImages(file, path);
		} catch (IOException e) {
			throw new FileUploadException();
		}
	}

	public ImageFileRdo getImageFile(String path) {
		fileUtils.isValidPath(path);

		try {
			Path filePath = Paths.get(directory + path);
			return ImageFileRdo.builder()
				.image(Files.readAllBytes(filePath))
				.mimeType(Files.probeContentType(filePath))
				.build();
		} catch (IOException e) {
			throw new FilePathInvalidException();
		}
	}
}
