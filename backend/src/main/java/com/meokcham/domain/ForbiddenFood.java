package com.meokcham.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "forbidden_foods")
public class ForbiddenFood {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false, length = 50)
	private String name;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	/** 소프트 삭제 시각. 과거 기록 스냅샷의 음식 id를 계속 추적하기 위해 행은 남긴다. */
	private Instant deletedAt;

	protected ForbiddenFood() {
	}

	public ForbiddenFood(User user, String name) {
		this.user = user;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getName() {
		return name;
	}

	public void rename(String name) {
		this.name = name;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public void delete() {
		this.deletedAt = Instant.now();
	}

	public FoodRef toRef() {
		return new FoodRef(id, name);
	}
}
