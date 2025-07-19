package com.seoulchonnom.slcnapp.depot.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.depot.DepotConstant.FILE_PATH_INVALID_ERROR_MESSAGE;

public class FilePathInvalidException extends BadRequestException {
    public FilePathInvalidException() {
        super(FILE_PATH_INVALID_ERROR_MESSAGE);
    }
}