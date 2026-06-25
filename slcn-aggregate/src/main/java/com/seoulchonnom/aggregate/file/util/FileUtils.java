package com.seoulchonnom.aggregate.file.util;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.file.exception.FileExtException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileSizeException;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;

@Component
public class FileUtils {
	@Value("${upload.path}")
	private String directory;

	public FileReference saveImages(MultipartFile multipartFile, String type) throws IOException {
		return saveImageAsset(multipartFile, type).toFileReference();
	}

	public FileAsset saveImageAsset(MultipartFile multipartFile, String type) throws IOException {
		if (type == null || type.isEmpty() || !type.matches(AVAILABLE_PATH)) {
			throw new FilePathInvalidException();
		}

		if (multipartFile.getSize() > MAX_FILE_SIZE) {
			throw new FileSizeException();
		}

		validateImageFile(multipartFile);

		String filename = createSaveFileName(multipartFile.getOriginalFilename());
		Path saveDirectory = Paths.get(directory).resolve(type).normalize();
		Files.createDirectories(saveDirectory);
		String saveFileName = saveDirectory.resolve(filename).toString();

		multipartFile.transferTo(new File(saveFileName));

		return new FileAsset(
			FileType.from(type),
			multipartFile.getOriginalFilename(),
			filename,
			multipartFile.getContentType(),
			multipartFile.getSize()
		);
	}

	public void isValidFilePath(String path) {
		if (path == null || path.isEmpty() || !path.matches(FILE_PATH_REGEX_STRING)) {
			throw new FilePathInvalidException();
		}
	}

	public void isValidFileRef(String type, String filename) {
		if (type == null || type.isEmpty() || !type.matches(AVAILABLE_PATH) ||
			filename == null || filename.isEmpty() || !filename.matches(FILE_NAME_REGEX_STRING)) {
			throw new FilePathInvalidException();
		}
	}

	private String createSaveFileName(String originalFilename) {
		String ext = extractExt(originalFilename);
		String uuid = UUID.randomUUID().toString();
		return uuid + '.' + ext;
	}

	private String extractExt(String originalFilename) {
		if (!StringUtils.hasText(originalFilename)) {
			throw new FileExtException();
		}

		int pos = originalFilename.lastIndexOf(".");
		if (pos < 0 || pos == originalFilename.length() - 1) {
			throw new FileExtException();
		}
		return originalFilename.substring(pos + 1).toLowerCase(Locale.ROOT);
	}

	private void validateImageFile(MultipartFile multipartFile) throws IOException {
		String ext = extractExt(multipartFile.getOriginalFilename());
		if (!ext.matches(EXT_REGEX_STRING)) {
			throw new FileExtException();
		}

		String contentType = multipartFile.getContentType();
		if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
			throw new FileExtException();
		}

		if ("svg".equals(ext)) {
			validateSvg(multipartFile);
			return;
		}

		try (InputStream inputStream = multipartFile.getInputStream()) {
			BufferedImage image = ImageIO.read(inputStream);
			if (image == null) {
				throw new FileExtException();
			}
		}
	}

	private void validateSvg(MultipartFile multipartFile) throws IOException {
		byte[] header;
		try (InputStream inputStream = multipartFile.getInputStream()) {
			header = inputStream.readNBytes(1024);
		}

		String content = new String(header, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
		if (!content.contains("<svg")) {
			throw new FileExtException();
		}
	}
}
