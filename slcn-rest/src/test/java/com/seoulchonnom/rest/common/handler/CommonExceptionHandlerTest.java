package com.seoulchonnom.rest.common.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.common.response.ErrorResponse;

class CommonExceptionHandlerTest {
	@Test
	void mapsBusinessExceptionUsingErrorCodeStatus() {
		CommonExceptionHandler handler = new CommonExceptionHandler();

		ResponseEntity<ErrorResponse> response = handler.businessException(new InvalidUserException());

		assertEquals(400, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals("로그인 정보가 잘못되었습니다.", response.getBody().getMessage());
	}

	@Test
	void mapsMethodArgumentNotValidExceptionToBadRequest() {
		CommonExceptionHandler handler = new CommonExceptionHandler();
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = mock(BindingResult.class);
		FieldError fieldError = new FieldError("tripCdo", "quiz.title", "나들이 퀴즈 타이틀은 필수값 입니다.");
		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

		ResponseEntity<ErrorResponse> response = handler.methodArgumentNotValidException(exception);

		assertEquals(400, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals("나들이 퀴즈 타이틀은 필수값 입니다.", response.getBody().getMessage());
	}

	@Test
	void mapsUnhandledExceptionToInternalServerError() {
		CommonExceptionHandler handler = new CommonExceptionHandler();

		ResponseEntity<ErrorResponse> response = handler.exception(new RuntimeException("boom"));

		assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus(), response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), response.getBody().getMessage());
	}

	@Test
	void mapsIllegalArgumentExceptionToBadRequest() {
		CommonExceptionHandler handler = new CommonExceptionHandler();

		ResponseEntity<ErrorResponse> response = handler.illegalArgumentException(new IllegalArgumentException("bad"));

		assertEquals(400, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals("입력이 올바르지 않습니다.", response.getBody().getMessage());
	}

	/**
	 * DataIntegrityViolationException은 더 이상 앱 전역에서 캘린더-일정 409로 매핑되지 않는다.
	 * uk_trip_date, username, client_id, token_hash 등 다른 unique 제약 위반이나 hidden
	 * NOT NULL 위반까지 이 메시지로 가려지는 것을 막기 위함이다. 이제는 예외가 잡히지 않으므로
	 * mapsUnhandledExceptionToInternalServerError가 검증하는 일반 Exception 경로(500)로
	 * 떨어진다. 캘린더 삭제 레이스에 대한 409 변환은 CalendarLogicTest에서 검증한다.
	 */
}
