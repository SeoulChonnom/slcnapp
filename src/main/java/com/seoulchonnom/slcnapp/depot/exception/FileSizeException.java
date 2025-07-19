package com.seoulchonnom.slcnapp.depot.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.depot.DepotConstant.FILE_SIZE_ERROR_MESSAGE;

public class FileSizeException extends BadRequestException {
    public FileSizeException() {
        super(FILE_SIZE_ERROR_MESSAGE);
    }
}