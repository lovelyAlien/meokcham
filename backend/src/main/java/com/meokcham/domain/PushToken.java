package com.meokcham.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_tokens")
public class PushToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	/** 전역 유니크. 같은 기기에서 다른 계정으로 로그인하면 소유자를 옮긴다. */
	@Column(nullable = false)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PushPlatform platform;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastSeenAt = Instant.now();

	protected PushToken() {
	}

	public PushToken(User user, String token, PushPlatform platform) {
		this.user = user;
		this.token = token;
		this.platform = platform;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getToken() {
		return token;
	}

	public PushPlatform getPlatform() {
		return platform;
	}

	public void moveTo(User user, PushPlatform platform) {
		this.user = user;
		this.platform = platform;
		this.lastSeenAt = Instant.now();
	}
}
