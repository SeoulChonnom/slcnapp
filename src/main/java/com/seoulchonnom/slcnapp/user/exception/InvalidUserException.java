package com.seoulchonnom.slcnapp.user.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.user.UserConstant.USERNAME_NOT_FOUND_ERROR_MESSAGE;

public class InvalidUserException extends BadRequestException {
    public InvalidUserException() {
        super(USERNAME_NOT_FOUND_ERROR_MESSAGE);
    }
}
