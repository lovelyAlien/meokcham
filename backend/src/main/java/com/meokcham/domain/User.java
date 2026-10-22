package com.meokcham.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** IANA 타임존 이름(예: Asia/Seoul). 유효성은 서비스에서 ZoneId.of()로 검증한다. */
	@Column(nullable = false, length = 64)
	private String timezone;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String timezone) {
		this.timezone = timezone;
	}

	public Long getId() {
		return id;
	}

	public String getTimezone() {
		return timezone;
	}

	public void changeTimezone(String timezone) {
		this.timezone = timezone;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
