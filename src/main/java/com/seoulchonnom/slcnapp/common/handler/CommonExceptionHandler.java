package com.seoulchonnom.slcnapp.common.handler;

import com.seoulchonnom.slcnapp.common.dto.ErrorResponse;
import com.seoulchonnom.slcnapp.common.exception.BadRequestException;
import com.seoulchonnom.slcnapp.common.exception.InternalServerErrorException;
import com.seoulchonnom.slcnapp.common.exception.PayloadTooLargeException;
import com.seoulchonnom.slcnapp.common.exception.UnsupportedMediaTypeException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CommonExceptionHandler {
	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ErrorResponse> badRequest(BadRequestException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, e.getMessage()),
			HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InternalServerErrorException.class)
	public ResponseEntity<ErrorResponse> internalServerErrorException(InternalServerErrorException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, e.getMessage()),
			HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	public ResponseEntity<ErrorResponse> payloadTooLargeException(PayloadTooLargeException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, e.getMessage()),
			HttpStatus.PAYLOAD_TOO_LARGE);
	}

	@ExceptionHandler(UnsupportedMediaTypeException.class)
	public ResponseEntity<ErrorResponse> unsupportedMediaTypeException(UnsupportedMediaTypeException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, e.getMessage()),
			HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> httpMessageNotReadableException(HttpMessageNotReadableException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, "입력이 올바르지 않습니다."),
			HttpStatus.BAD_REQUEST);
	}
}
