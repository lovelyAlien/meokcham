package com.meokcham.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.meokcham.dto.UpdateTimezoneRequest;
import com.meokcham.dto.UserResponse;
import com.meokcham.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	UserResponse get(@AuthenticationPrincipal Jwt jwt) {
		return UserResponse.from(userService.get(userId(jwt)));
	}

	@PatchMapping
	UserResponse changeTimezone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateTimezoneRequest request) {
		return UserResponse.from(userService.changeTimezone(userId(jwt), request.timezone()));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt) {
		userService.delete(userId(jwt));
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}
}
