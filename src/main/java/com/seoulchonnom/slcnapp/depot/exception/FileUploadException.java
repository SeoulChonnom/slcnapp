package com.seoulchonnom.slcnapp.depot.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.depot.DepotConstant.FILE_UPLOAD_ERROR_MESSAGE;

public class FileUploadException extends BadRequestException {
    public FileUploadException() {
        super(FILE_UPLOAD_ERROR_MESSAGE);
    }
}