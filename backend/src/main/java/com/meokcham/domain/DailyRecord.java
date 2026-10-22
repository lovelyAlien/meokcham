package com.meokcham.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

/**
 * 하루 한 건의 기록. 저장(upsert)은 JPA가 아니라 JdbcClient의 ON CONFLICT로 하고,
 * 이 엔티티는 조회와 스냅샷 매핑에 쓴다.
 */
@Entity
@Table(name = "daily_records")
public class DailyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	/** 사용자 타임존 기준 날짜(하루 경계는 로컬 새벽 4시). */
	@Column(nullable = false)
	private LocalDate localDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RecordStatus status;

	/** 기록 시점의 금지 목록. 이후 음식을 바꾸거나 지워도 과거 기록의 의미는 유지된다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<FoodRef> forbiddenSnapshot;

	/** 먹은 음식. KEPT면 비어 있고 BROKEN이면 하나 이상 있다(DB CHECK로 강제). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<FoodRef> eatenFoods;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected DailyRecord() {
	}

	public DailyRecord(User user, LocalDate localDate, List<FoodRef> forbiddenSnapshot, List<FoodRef> eatenFoods) {
		this.user = user;
		this.localDate = localDate;
		this.forbiddenSnapshot = forbiddenSnapshot;
		this.eatenFoods = eatenFoods;
		this.status = eatenFoods.isEmpty() ? RecordStatus.KEPT : RecordStatus.BROKEN;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public LocalDate getLocalDate() {
		return localDate;
	}

	public RecordStatus getStatus() {
		return status;
	}

	public List<FoodRef> getForbiddenSnapshot() {
		return forbiddenSnapshot;
	}

	public List<FoodRef> getEatenFoods() {
		return eatenFoods;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
