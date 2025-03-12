package com.seoulchonnom.slcnapp.common.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.common.exception.BadRequestException;
import com.seoulchonnom.slcnapp.common.exception.InternalServerErrorException;
import com.seoulchonnom.slcnapp.common.exception.PayloadTooLargeException;
import com.seoulchonnom.slcnapp.common.exception.UnsupportedMediaTypeException;

@RestControllerAdvice
public class CommonExceptionHandler {
	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<BaseResponse> badRequest(BadRequestException e) {
		return new ResponseEntity<>(
			BaseResponse.from(false, e.getMessage()),
			HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InternalServerErrorException.class)
	public ResponseEntity<BaseResponse> internalServerErrorException(InternalServerErrorException e) {
		return new ResponseEntity<>(
			BaseResponse.from(false, e.getMessage()),
			HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	public ResponseEntity<BaseResponse> payloadTooLargeException(PayloadTooLargeException e) {
		return new ResponseEntity<>(
			BaseResponse.from(false, e.getMessage()),
			HttpStatus.PAYLOAD_TOO_LARGE);
	}

	@ExceptionHandler(UnsupportedMediaTypeException.class)
	public ResponseEntity<BaseResponse> unsupportedMediaTypeException(UnsupportedMediaTypeException e) {
		return new ResponseEntity<>(
			BaseResponse.from(false, e.getMessage()),
			HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}
}
