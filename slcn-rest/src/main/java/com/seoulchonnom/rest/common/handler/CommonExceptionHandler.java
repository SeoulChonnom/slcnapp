package com.seoulchonnom.rest.common.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.response.ErrorResponse;

@RestControllerAdvice
public class CommonExceptionHandler {
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> businessException(BusinessException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, e.getMessage()),
			e.getErrorCode().getHttpStatus());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> httpMessageNotReadableException(HttpMessageNotReadableException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, "입력이 올바르지 않습니다."),
				HttpStatus.BAD_REQUEST);
	}
}
