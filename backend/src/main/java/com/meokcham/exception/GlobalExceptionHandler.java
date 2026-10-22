package com.meokcham.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러를 ProblemDetail(RFC 9457) + {@code code} 필드로 내려준다. 형식은 docs/API_SPEC.md 1.2 참고.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<Object> handleBusiness(BusinessException e, WebRequest request) {
		ErrorCode code = e.errorCode();
		ProblemDetail body = problem(code, e.getMessage());
		return handleExceptionInternal(e, body, new HttpHeaders(), code.status(), request);
	}

	// 내부 정보(스택, SQL 등)는 응답에 담지 않고 로그로만 남긴다.
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception e, WebRequest request) {
		log.error("처리되지 않은 예외", e);
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		return handleExceptionInternal(e, problem(code, code.defaultDetail()), new HttpHeaders(), code.status(), request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultDetail());
		List<FieldViolation> errors = e.getBindingResult().getFieldErrors().stream()
				.map(GlobalExceptionHandler::toViolation)
				.toList();
		body.setProperty("errors", errors);
		return handleExceptionInternal(e, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	// 스프링 MVC가 직접 던지는 나머지 예외(잘못된 JSON, 405 등)에도 code를 붙인다.
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
		if (response != null && response.getBody() instanceof ProblemDetail detail
				&& (detail.getProperties() == null || !detail.getProperties().containsKey("code"))) {
			detail.setProperty("code", status.value() == 400 ? ErrorCode.VALIDATION_FAILED.name()
					: HttpStatus.valueOf(status.value()).name());
		}
		return response;
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setProperty("code", code.name());
		return problem;
	}

	private static FieldViolation toViolation(FieldError error) {
		return new FieldViolation(error.getField(), error.getDefaultMessage());
	}

	record FieldViolation(String field, String message) {
	}
}
