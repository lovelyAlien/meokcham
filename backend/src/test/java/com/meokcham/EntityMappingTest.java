package com.meokcham;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.meokcham.domain.DailyRecord;
import com.meokcham.domain.FoodRef;
import com.meokcham.domain.ForbiddenFood;
import com.meokcham.domain.NotificationSetting;
import com.meokcham.domain.PushPlatform;
import com.meokcham.domain.PushToken;
import com.meokcham.domain.RecordStatus;
import com.meokcham.domain.RefreshToken;
import com.meokcham.domain.SocialAccount;
import com.meokcham.domain.SocialProvider;
import com.meokcham.domain.User;

import jakarta.persistence.EntityManager;

/**
 * 엔티티 매핑이 V1 스키마와 맞는지(기동 시 validate) 확인하고, jsonb·MapsId·char(64) 같은 까다로운 매핑을 왕복 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EntityMappingTest {

	@Autowired
	EntityManager em;

	@Test
	void dailyRecordRoundTripsJsonbSnapshotAndDerivesStatus() {
		User user = persist(new User("Asia/Seoul"));
		ForbiddenFood ramen = persist(new ForbiddenFood(user, "라면"));
		ForbiddenFood soda = persist(new ForbiddenFood(user, "탄산"));
		List<FoodRef> snapshot = List.of(ramen.toRef(), soda.toRef());

		DailyRecord broken = persist(new DailyRecord(user, LocalDate.of(2026, 10, 20), snapshot, List.of(ramen.toRef())));
		DailyRecord kept = persist(new DailyRecord(user, LocalDate.of(2026, 10, 21), snapshot, List.of()));
		em.flush();
		em.clear();

		DailyRecord found = em.find(DailyRecord.class, broken.getId());
		assertThat(found.getStatus()).isEqualTo(RecordStatus.BROKEN);
		assertThat(found.getForbiddenSnapshot()).containsExactly(ramen.toRef(), soda.toRef());
		assertThat(found.getEatenFoods()).containsExactly(ramen.toRef());
		assertThat(em.find(DailyRecord.class, kept.getId()).getStatus()).isEqualTo(RecordStatus.KEPT);
	}

	@Test
	void notificationSettingSharesPrimaryKeyWithUserAndHasDefaults() {
		User user = persist(new User("Asia/Seoul"));
		persist(new NotificationSetting(user));
		em.flush();
		em.clear();

		NotificationSetting found = em.find(NotificationSetting.class, user.getId());
		assertThat(found.isEnabled()).isTrue();
		assertThat(found.getNotifyTime()).isEqualTo(LocalTime.of(21, 0));
	}

	@Test
	void refreshTokenMapsFixedWidthHashAndUuid() {
		User user = persist(new User("Asia/Seoul"));
		String hash = "a".repeat(64);
		UUID family = UUID.randomUUID();
		RefreshToken token = persist(new RefreshToken(user, hash, family, Instant.now().plusSeconds(60)));
		em.flush();
		em.clear();

		RefreshToken found = em.find(RefreshToken.class, token.getId());
		assertThat(found.getTokenHash()).isEqualTo(hash);
		assertThat(found.getFamilyId()).isEqualTo(family);
		assertThat(found.isRevoked()).isFalse();
	}

	@Test
	void remainingEntitiesPersist() {
		User user = persist(new User("Asia/Seoul"));
		persist(new SocialAccount(user, SocialProvider.APPLE, "sub-1", null));
		persist(new PushToken(user, "ExponentPushToken[x]", PushPlatform.IOS));
		ForbiddenFood food = persist(new ForbiddenFood(user, "라면"));
		food.delete();
		em.flush();
		em.clear();

		assertThat(em.find(ForbiddenFood.class, food.getId()).isDeleted()).isTrue();
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}
}
