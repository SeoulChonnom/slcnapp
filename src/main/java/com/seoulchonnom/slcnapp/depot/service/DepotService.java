package com.seoulchonnom.slcnapp.depot.service;

import com.seoulchonnom.slcnapp.depot.dto.ImageFile;
import com.seoulchonnom.slcnapp.depot.exception.FilePathInvalidException;
import com.seoulchonnom.slcnapp.depot.exception.FileUploadException;
import com.seoulchonnom.slcnapp.depot.util.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Transactional
public class DepotService {
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

    public ImageFile getImageFile(String path) {
        try {
            Path filePath = Paths.get(directory + path);
            return ImageFile.builder()
                    .image(Files.readAllBytes(filePath))
                    .mimeType(Files.probeContentType(filePath))
                    .build();
        } catch (IOException e) {
            throw new FilePathInvalidException();
        }
    }
}