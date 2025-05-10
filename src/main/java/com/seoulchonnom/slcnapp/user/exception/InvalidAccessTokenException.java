package com.seoulchonnom.slcnapp.user.exception;

import com.seoulchonnom.slcnapp.common.exception.InternalServerErrorException;

import static com.seoulchonnom.slcnapp.user.UserConstant.ACCESS_TOKEN_INVALID_ERROR_MESSAGE;

public class InvalidAccessTokenException extends InternalServerErrorException {
    public InvalidAccessTokenException() {
        super(ACCESS_TOKEN_INVALID_ERROR_MESSAGE);
    }
}