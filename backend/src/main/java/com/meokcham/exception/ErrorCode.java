package com.meokcham.exception;

import org.springframework.http.HttpStatus;

/**
 * API 에러 코드. docs/API_SPEC.md 9장과 같은 목록을 유지한다.
 */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰이 올바르지 않습니다."),
	ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 올바르지 않습니다."),
	FOOD_NOT_FOUND(HttpStatus.NOT_FOUND, "금지 음식을 찾을 수 없습니다."),
	RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 날짜의 기록이 없습니다."),
	FOOD_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 같은 이름의 금지 음식이 있습니다."),
	FOOD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "금지 음식은 최대 10개까지 등록할 수 있습니다."),
	NO_FORBIDDEN_FOODS(HttpStatus.CONFLICT, "등록된 금지 음식이 없어 기록할 수 없습니다."),
	RECORD_DATE_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "미래 날짜는 기록할 수 없습니다."),
	UNKNOWN_FOOD(HttpStatus.UNPROCESSABLE_CONTENT, "등록된 금지 음식이 아닌 항목이 있습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultDetail;

	ErrorCode(HttpStatus status, String defaultDetail) {
		this.status = status;
		this.defaultDetail = defaultDetail;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultDetail() {
		return defaultDetail;
	}
}
