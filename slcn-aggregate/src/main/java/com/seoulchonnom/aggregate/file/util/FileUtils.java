package com.seoulchonnom.aggregate.file.util;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.file.exception.FileExtException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileSizeException;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;

@Component
public class FileUtils {
	@Value("${upload.path}")
	private String directory;

	public FileReference saveImages(MultipartFile multipartFile, String type) throws IOException {

		if (type == null || type.isEmpty() || !type.matches(AVAILABLE_PATH)) {
			throw new FilePathInvalidException();
		}

		if (multipartFile.getSize() > MAX_FILE_SIZE) {
			throw new FileSizeException();
		}

		if (!isImage(multipartFile.getOriginalFilename())) {
			throw new FileExtException();
		}

		String filename = createSaveFileName(multipartFile.getOriginalFilename());
		String saveFileName = directory + type + '/' + filename;

		multipartFile.transferTo(new File(saveFileName));

		return new FileReference(FileType.from(type), filename);
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
		int pos = originalFilename.lastIndexOf(".");
		return originalFilename.substring(pos + 1);
	}

	private boolean isImage(String originalFilename) {

		return extractExt(originalFilename).matches(EXT_REGEX_STRING);
	}
}
