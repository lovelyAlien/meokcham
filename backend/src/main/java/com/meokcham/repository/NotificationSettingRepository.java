package com.meokcham.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.meokcham.domain.NotificationSetting;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
}
