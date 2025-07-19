package com.seoulchonnom.slcnapp.depot.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.depot.DepotConstant.FILE_EXT_ERROR_MESSAGE;

public class FileExtException extends BadRequestException {
    public FileExtException() {
        super(FILE_EXT_ERROR_MESSAGE);
    }
}