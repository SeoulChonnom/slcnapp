package com.seoulchonnom.rest.common.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.common.response.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
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

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> methodArgumentNotValidException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().isEmpty()
			? "입력이 올바르지 않습니다."
			: e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
		return new ResponseEntity<>(ErrorResponse.from(false, message), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> missingServletRequestParameterException(MissingServletRequestParameterException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, e.getParameterName() + " 파라미터가 필요합니다."),
			HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ErrorResponse> bindException(BindException e) {
		String message = e.getBindingResult().getFieldErrors().isEmpty()
			? "입력이 올바르지 않습니다."
			: e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
		return new ResponseEntity<>(ErrorResponse.from(false, message), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> httpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, "지원하지 않는 HTTP 메서드입니다."),
			HttpStatus.METHOD_NOT_ALLOWED);
	}

	/**
	 * 쿼리 파라미터를 enum이나 숫자로 변환하지 못한 경우다.
	 * MethodArgumentTypeMismatchException은 IllegalArgumentException의 하위 타입이 아니라
	 * 따로 잡지 않으면 잘못된 입력이 500으로 나간다.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> methodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, e.getName() + " 값이 올바르지 않습니다."),
			HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> illegalArgumentException(IllegalArgumentException e) {
		return new ResponseEntity<>(
			ErrorResponse.from(false, "입력이 올바르지 않습니다."),
			HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> exception(Exception e) {
		log.error("Unhandled exception", e);
		return new ResponseEntity<>(
			ErrorResponse.from(false, ErrorCode.INTERNAL_SERVER_ERROR.getMessage()),
			ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
	}
}
