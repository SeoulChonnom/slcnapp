package com.seoulchonnom.slcnapp.depot.util;

import com.seoulchonnom.slcnapp.depot.exception.FileExtException;
import com.seoulchonnom.slcnapp.depot.exception.FilePathInvalidException;
import com.seoulchonnom.slcnapp.depot.exception.FileSizeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.seoulchonnom.slcnapp.depot.DepotConstant.EXT_REGEX_STRING;
import static com.seoulchonnom.slcnapp.depot.DepotConstant.MAX_FILE_SIZE;

@Component
public class FileUtils {
    @Value("${upload.path}")
    private String directory;
    private final String AVAILABLE_PATH = "logo|map";

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

        if (extractExt(originalFilename).matches(EXT_REGEX_STRING)) {
            return true;
        }
        return false;

    }
}