package com.seoulchonnom.slcnapp.depot.controller;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.depot.dto.ImageFile;
import com.seoulchonnom.slcnapp.depot.service.DepotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.seoulchonnom.slcnapp.depot.DepotConstant.FILE_UPLOAD_SUCCESS_MESSAGE;

@RestController
@RequestMapping("/depot")
@RequiredArgsConstructor
public class DepotController implements DepotControllerDocs {
    private final DepotService depotService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path) {
        String fileName = depotService.uploadFile(file, path);
        return new ResponseEntity<>(
                BaseResponse.from(true, FILE_UPLOAD_SUCCESS_MESSAGE, fileName),
                HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<byte[]> getFile(@RequestParam(value = "path") String path) {
        ImageFile imageFile = depotService.getImageFile(path);

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.valueOf(imageFile.getMimeType()))
                .body(imageFile.getImage());
    }
}