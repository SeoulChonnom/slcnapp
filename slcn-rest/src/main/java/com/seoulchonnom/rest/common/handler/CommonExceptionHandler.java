package com.seoulchonnom.rest.common.handler;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.common.response.ErrorResponse;
import com.seoulchonnom.spec.common.response.ErrorResponse.Violation;

import lombok.extern.slf4j.Slf4j;

/**
 * 모든 예외를 ErrorResponse(title, status, code, errors) 한 가지 형태로 바꾼다.
 * code는 항상 ErrorCode 이름이라 FE가 이것만으로 분기할 수 있다.
 *
 * Content-Type은 Accept와 상관없이 application/json으로 고정한다. 본문 모양이 ProblemDetail과
 * 비슷해 Jackson이 Accept: application/problem+json 요청에 그 타입을 골라 줄 수 있는데,
 * 우리 형식은 RFC 9457 문서가 아니므로 타입이 요청마다 달라지지 않게 한다.
 */
@RestControllerAdvice
@Slf4j
public class CommonExceptionHandler {
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> businessException(BusinessException e) {
		return respond(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> httpMessageNotReadableException(HttpMessageNotReadableException e) {
		return respond(ErrorResponse.of(ErrorCode.INVALID_REQUEST_BODY));
	}

	/**
	 * MethodArgumentNotValidException(@Valid 본문)도 BindException의 하위 타입이라 여기서 함께 받는다.
	 * title에는 첫 번째 필드 메시지를 올려 기존처럼 그대로 보여 줄 수 있게 하고, 전체 목록은 errors에 싣는다.
	 */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ErrorResponse> bindException(BindException e) {
		List<Violation> violations = violationsOf(e.getBindingResult());
		String title = violations.isEmpty()
			? ErrorCode.VALIDATION_FAILED.getMessage()
			: violations.get(0).message();
		return respond(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, title, violations));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> missingServletRequestParameterException(
		MissingServletRequestParameterException e) {
		String message = e.getParameterName() + " 파라미터가 필요합니다.";
		return respond(ErrorResponse.of(ErrorCode.MISSING_PARAMETER, message,
			List.of(new Violation(e.getParameterName(), "REQUIRED", message))));
	}

	/**
	 * 없는 경로. 잡지 않으면 아래 Exception 핸들러가 500으로 바꾼다.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> noResourceFoundException(NoResourceFoundException e) {
		return respond(ErrorResponse.of(ErrorCode.NOT_FOUND));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> httpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
		return respond(ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> httpRequestMethodNotSupportedException(
		HttpRequestMethodNotSupportedException e) {
		return respond(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
	}

	/**
	 * 쿼리 파라미터를 enum이나 숫자로 변환하지 못한 경우다.
	 * MethodArgumentTypeMismatchException은 IllegalArgumentException의 하위 타입이 아니라
	 * 따로 잡지 않으면 잘못된 입력이 500으로 나간다.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> methodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
		String message = e.getName() + " 값이 올바르지 않습니다.";
		return respond(ErrorResponse.of(ErrorCode.INVALID_PARAMETER, message,
			List.of(new Violation(e.getName(), "TYPE_MISMATCH", message))));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> illegalArgumentException(IllegalArgumentException e) {
		return respond(ErrorResponse.of(ErrorCode.BAD_REQUEST, "입력이 올바르지 않습니다."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> exception(Exception e) {
		log.error("Unhandled exception", e);
		return respond(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
	}

	private ResponseEntity<ErrorResponse> respond(ErrorResponse body) {
		return ResponseEntity.status(body.getStatus()).contentType(MediaType.APPLICATION_JSON).body(body);
	}

	private List<Violation> violationsOf(BindingResult bindingResult) {
		return bindingResult.getFieldErrors().stream()
			.map(fieldError -> new Violation(fieldError.getField(), toViolationCode(fieldError.getCode()),
				fieldError.getDefaultMessage()))
			.toList();
	}

	/**
	 * Bean Validation 제약 이름(NotBlank, typeMismatch)을 ErrorCode와 같은 대문자 스네이크 케이스로 맞춘다.
	 */
	static String toViolationCode(String constraintCode) {
		if (constraintCode == null || constraintCode.isBlank()) {
			return "INVALID";
		}
		return constraintCode.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
	}
}
