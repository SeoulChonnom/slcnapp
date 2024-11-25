package com.seoulchonnom.slcnapp.common.aop;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

@RestControllerAdvice
public class ExceptionAdvice {
	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<BaseResponse> badRequest(BadRequestException e) {
		return new ResponseEntity<>(
			BaseResponse.from(false, e.getMessage()),
			HttpStatus.BAD_REQUEST
		);
	}
}
