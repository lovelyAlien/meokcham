package com.meokcham.service;

/** 소셜 ID 토큰을 검증해 얻은 신원. subject가 사용자를 식별하는 값이다. */
public record SocialIdentity(String subject, String email) {
}
