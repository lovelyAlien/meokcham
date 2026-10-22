package com.meokcham.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

	@Id
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false)
	private boolean enabled = true;

	/** 사용자 로컬 시각. */
	@Column(nullable = false)
	private LocalTime notifyTime = LocalTime.of(21, 0);

	/** 마지막으로 알림을 보낸 로컬 날짜. 하루 한 번만 보내기 위한 선점 표시다. */
	private LocalDate lastNotifiedOn;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected NotificationSetting() {
	}

	public NotificationSetting(User user) {
		this.user = user;
	}

	public Long getUserId() {
		return userId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public LocalTime getNotifyTime() {
		return notifyTime;
	}

	public LocalDate getLastNotifiedOn() {
		return lastNotifiedOn;
	}

	public void update(boolean enabled, LocalTime notifyTime) {
		this.enabled = enabled;
		this.notifyTime = notifyTime;
	}
}
