package com.meokcham;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.meokcham.exception.BusinessException;
import com.meokcham.exception.ErrorCode;
import com.meokcham.exception.GlobalExceptionHandler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 에러 응답이 API 명세 1.2의 형식(ProblemDetail + code)을 따르는지 검증한다.
 */
class GlobalExceptionHandlerTest {

	MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void businessExceptionUsesErrorCodeStatusAndCode() throws Exception {
		mvc.perform(get("/probe/business"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FOOD_NAME_DUPLICATED"))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("이미 같은 이름의 금지 음식이 있습니다."));
	}

	@Test
	void validationFailureListsFieldErrors() throws Exception {
		mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void malformedJsonGetsValidationFailedCode() throws Exception {
		mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unexpectedExceptionHidesInternalDetails() throws Exception {
		mvc.perform(get("/probe/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("서버 오류가 발생했습니다."));
	}

	@Test
	void unsupportedMethodGetsStatusNameAsCode() throws Exception {
		mvc.perform(post("/probe/business"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@RestController
	static class ProbeController {

		@GetMapping("/probe/business")
		void business() {
			throw new BusinessException(ErrorCode.FOOD_NAME_DUPLICATED);
		}

		@PostMapping("/probe/validate")
		void validate(@Valid @RequestBody Body body) {
		}

		@GetMapping("/probe/boom")
		void boom() {
			throw new IllegalStateException("secret internal message");
		}
	}

	record Body(@NotBlank String name) {
	}
}
