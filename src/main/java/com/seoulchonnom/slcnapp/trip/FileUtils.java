package com.seoulchonnom.slcnapp.trip;

import com.seoulchonnom.slcnapp.trip.exception.TripFileExtException;
import com.seoulchonnom.slcnapp.trip.exception.TripFileSizeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.seoulchonnom.slcnapp.trip.TripConstant.EXT_REGEX_STRING;
import static com.seoulchonnom.slcnapp.trip.TripConstant.MAX_FILE_SIZE;

@Component
public class FileUtils {
	@Value("${upload.path}")
	private String directory;

	public String saveImages(MultipartFile multipartFile, String path) throws IOException {

		if (multipartFile.getSize() > MAX_FILE_SIZE) {
			throw new TripFileSizeException();
		}

		if (!isImage(multipartFile.getOriginalFilename())) {
			throw new TripFileExtException();
		}

		String fileName = createSaveFileName(path, multipartFile.getOriginalFilename());
		String saveFileName = directory + fileName;

		multipartFile.transferTo(new File(saveFileName));

		return fileName;
	}

	// 파일 저장 이름 만들기
	// - 사용자들이 올리는 파일 이름이 같을 수 있으므로, 자체적으로 랜덤 이름을 만들어 사용한다
	private String createSaveFileName(String path, String originalFilename) {
		String ext = extractExt(originalFilename);
		String uuid = UUID.randomUUID().toString();
		return path + uuid + '.' + ext;
	}

	// 확장자명 구하기
	private String extractExt(String originalFilename) {
		int pos = originalFilename.lastIndexOf(".");
		return originalFilename.substring(pos + 1);
	}

	private boolean isImage(String originalFilename) {

		if (extractExt(originalFilename).matches(EXT_REGEX_STRING)) {
			return true;
		}
		return false;

	}
}
