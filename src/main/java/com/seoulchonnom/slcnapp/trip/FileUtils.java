package com.seoulchonnom.slcnapp.trip;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class FileUtils {
    @Value("${upload.path}")
    private String directory;

    public String saveImages(MultipartFile multipartFile, String path) throws IOException {
        String fileName = createSaveFileName(multipartFile.getOriginalFilename());
        String saveFileName = directory + path + fileName;

        multipartFile.transferTo(new File(saveFileName));

        return saveFileName;
    }

    // 파일 저장 이름 만들기
    // - 사용자들이 올리는 파일 이름이 같을 수 있으므로, 자체적으로 랜덤 이름을 만들어 사용한다
    private String createSaveFileName(String originalFilename) {
        String ext = extractExt(originalFilename);
        String uuid = UUID.randomUUID().toString();
        return uuid + "." + ext;
    }

    // 확장자명 구하기
    private String extractExt(String originalFilename) {
        int pos = originalFilename.lastIndexOf(".");
        return originalFilename.substring(pos + 1);
    }
}
