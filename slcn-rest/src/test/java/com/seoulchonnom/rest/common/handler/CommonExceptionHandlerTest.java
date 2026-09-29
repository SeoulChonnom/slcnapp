package com.seoulchonnom.rest.common.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDisabledException;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.common.response.ErrorResponse;
import com.seoulchonnom.spec.common.response.ErrorResponse.Violation;

class CommonExceptionHandlerTest {
	private final CommonExceptionHandler handler = new CommonExceptionHandler();

	@Test
	void businessException_shouldCarryErrorCodeNameAndStatus() {
		ResponseEntity<ErrorResponse> response = handler.businessException(new InvalidUserException());

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		ErrorResponse body = response.getBody();
		assertThat(body.getStatus()).isEqualTo(400);
		assertThat(body.getCode()).isEqualTo("INVALID_USER");
		assertThat(body.getTitle()).isEqualTo("로그인 정보가 잘못되었습니다.");
		assertThat(body.getErrors()).isEmpty();
	}

	/**
	 * FE가 메시지 문자열이 아니라 code로 원인을 가를 수 있어야 한다(분류 누락/비활성/재활성화 금지).
	 */
	@Test
	void businessException_shouldDistinguishInspectionQuestionCategoryCausesByCode() {
		ResponseEntity<ErrorResponse> response = handler.businessException(
			new InspectionQuestionCategoryDisabledException());

		assertThat(response.getBody().getCode()).isEqualTo("INSPECTION_QUESTION_CATEGORY_DISABLED");
		assertThat(response.getBody().getStatus()).isEqualTo(400);
	}

	@Test
	void bindException_shouldListEveryFieldViolationAndUseFirstMessageAsTitle() {
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = mock(BindingResult.class);
		FieldError titleError = new FieldError("tripCdo", "quiz.title", null, false,
			new String[] {"NotBlank.tripCdo.quiz.title", "NotBlank"}, null, "나들이 퀴즈 타이틀은 필수값 입니다.");
		FieldError dateError = new FieldError("tripCdo", "date", "2026-13-01", true,
			new String[] {"typeMismatch.tripCdo.date", "typeMismatch"}, null, "날짜 형식이 올바르지 않습니다.");
		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(titleError, dateError));

		ResponseEntity<ErrorResponse> response = handler.bindException(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		ErrorResponse body = response.getBody();
		assertThat(body.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
		assertThat(body.getTitle()).isEqualTo("나들이 퀴즈 타이틀은 필수값 입니다.");
		assertThat(body.getErrors()).containsExactly(
			new Violation("quiz.title", "NOT_BLANK", "나들이 퀴즈 타이틀은 필수값 입니다."),
			new Violation("date", "TYPE_MISMATCH", "날짜 형식이 올바르지 않습니다."));
	}

	@Test
	void missingServletRequestParameterException_shouldPointAtTheParameter() {
		ResponseEntity<ErrorResponse> response = handler.missingServletRequestParameterException(
			new MissingServletRequestParameterException("month", "String"));

		ErrorResponse body = response.getBody();
		assertThat(body.getCode()).isEqualTo(ErrorCode.MISSING_PARAMETER.name());
		assertThat(body.getErrors()).containsExactly(
			new Violation("month", "REQUIRED", "month 파라미터가 필요합니다."));
	}

	@Test
	void methodArgumentTypeMismatchException_shouldPointAtTheParameter() {
		MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
			"ABC", Integer.class, "page", mock(MethodParameter.class), null);

		ResponseEntity<ErrorResponse> response = handler.methodArgumentTypeMismatchException(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INVALID_PARAMETER.name());
		assertThat(response.getBody().getErrors()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("page", "TYPE_MISMATCH"));
	}

	/**
	 * 없는 경로가 아래 Exception 핸들러로 떨어져 500이 되던 문제를 막는다.
	 */
	@Test
	void noResourceFoundException_shouldMapToNotFound() {
		ResponseEntity<ErrorResponse> response = handler.noResourceFoundException(
			new NoResourceFoundException(HttpMethod.GET, "no-such-path"));

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.NOT_FOUND.name());
	}

	@Test
	void httpMediaTypeNotSupportedException_shouldMapToUnsupportedMediaType() {
		ResponseEntity<ErrorResponse> response = handler.httpMediaTypeNotSupportedException(
			new HttpMediaTypeNotSupportedException("text/plain"));

		assertThat(response.getStatusCode().value()).isEqualTo(415);
		assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name());
	}

	@Test
	void respond_shouldPinApplicationJsonContentType() {
		ResponseEntity<ErrorResponse> response = handler.businessException(new InvalidUserException());

		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	void exception_shouldMapUnhandledToInternalServerError() {
		ResponseEntity<ErrorResponse> response = handler.exception(new RuntimeException("boom"));

		assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
		assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
		assertThat(response.getBody().getTitle()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
	}

	@Test
	void illegalArgumentException_shouldMapToBadRequest() {
		ResponseEntity<ErrorResponse> response = handler.illegalArgumentException(new IllegalArgumentException("bad"));

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.BAD_REQUEST.name());
		assertThat(response.getBody().getTitle()).isEqualTo("입력이 올바르지 않습니다.");
	}

	@Test
	void toViolationCode_shouldConvertConstraintNamesToUpperSnakeCase() {
		assertThat(CommonExceptionHandler.toViolationCode("NotBlank")).isEqualTo("NOT_BLANK");
		assertThat(CommonExceptionHandler.toViolationCode("typeMismatch")).isEqualTo("TYPE_MISMATCH");
		assertThat(CommonExceptionHandler.toViolationCode("Size")).isEqualTo("SIZE");
		assertThat(CommonExceptionHandler.toViolationCode(null)).isEqualTo("INVALID");
	}

	/**
	 * DataIntegrityViolationException은 더 이상 앱 전역에서 캘린더-일정 409로 매핑되지 않는다.
	 * uk_trip_date, username, client_id, token_hash 등 다른 unique 제약 위반이나 hidden
	 * NOT NULL 위반까지 이 메시지로 가려지는 것을 막기 위함이다. 이제는 예외가 잡히지 않으므로
	 * exception_shouldMapUnhandledToInternalServerError가 검증하는 일반 Exception 경로(500)로
	 * 떨어진다. 캘린더 삭제 레이스에 대한 409 변환은 CalendarLogicTest에서 검증한다.
	 */
}
