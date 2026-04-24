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

@Component
public class FileUtils {
	@Value("${upload.path}")
	private String directory;

	public String saveImages(MultipartFile multipartFile, String path) throws IOException {

		if (path.isEmpty() || !path.matches(AVAILABLE_PATH)) {
			throw new FilePathInvalidException();
		}

		if (multipartFile.getSize() > MAX_FILE_SIZE) {
			throw new FileSizeException();
		}

		if (!isImage(multipartFile.getOriginalFilename())) {
			throw new FileExtException();
		}

		String fileName = createSaveFileName(path, multipartFile.getOriginalFilename());
		String saveFileName = directory + fileName;

		multipartFile.transferTo(new File(saveFileName));

		return fileName;
	}

	public void isValidFilePath(String path) {
		if (path.isEmpty() || !path.matches(FILE_PATH_REGEX_STRING)) {
			throw new FilePathInvalidException();
		}
	}

	private String createSaveFileName(String path, String originalFilename) {
		String ext = extractExt(originalFilename);
		String uuid = UUID.randomUUID().toString();
		return path + '/' + uuid + '.' + ext;
	}

	private String extractExt(String originalFilename) {
		int pos = originalFilename.lastIndexOf(".");
		return originalFilename.substring(pos + 1);
	}

	private boolean isImage(String originalFilename) {

		return extractExt(originalFilename).matches(EXT_REGEX_STRING);
	}
}