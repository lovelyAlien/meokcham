package com.meokcham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * V1 마이그레이션의 제약 조건이 PRD 규칙을 DB 수준에서 지키는지 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaConstraintsTest {

	private static final String SNAPSHOT = "[{\"id\": 1, \"name\": \"라면\"}, {\"id\": 2, \"name\": \"탄산\"}]";
	private static final String EATEN_RAMEN = "[{\"id\": 1, \"name\": \"라면\"}]";
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

	@Autowired
	JdbcClient jdbc;

	long userId;

	@BeforeEach
	void createUser() {
		userId = jdbc.sql("INSERT INTO users (timezone) VALUES ('Asia/Seoul') RETURNING id")
				.query(Long.class)
				.single();
	}

	@Test
	void upsertKeepsOneRecordPerDayWithLatestStatus() {
		upsertRecord("KEPT", "[]");
		upsertRecord("BROKEN", EATEN_RAMEN);

		var statuses = jdbc.sql("SELECT status FROM daily_records WHERE user_id = :userId AND local_date = :date")
				.param("userId", userId)
				.param("date", TODAY)
				.query(String.class)
				.list();
		assertThat(statuses).containsExactly("BROKEN");
	}

	@Test
	void plainInsertOfSecondRecordOnSameDayIsRejected() {
		insertRecord("KEPT", "[]");

		assertThatThrownBy(() -> insertRecord("KEPT", "[]"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void keptRecordCannotHaveEatenFoods() {
		assertThatThrownBy(() -> insertRecord("KEPT", EATEN_RAMEN))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void brokenRecordRequiresEatenFoods() {
		assertThatThrownBy(() -> insertRecord("BROKEN", "[]"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void activeForbiddenFoodNamesAreUniquePerUser() {
		insertFood("라면");

		assertThatThrownBy(() -> insertFood("라면"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void softDeletedFoodNameCanBeAddedAgain() {
		long foodId = insertFood("라면");
		jdbc.sql("UPDATE forbidden_foods SET deleted_at = now() WHERE id = :id").param("id", foodId).update();

		insertFood("라면");

		var activeCount = jdbc.sql("SELECT count(*) FROM forbidden_foods WHERE user_id = :userId AND deleted_at IS NULL")
				.param("userId", userId)
				.query(Long.class)
				.single();
		assertThat(activeCount).isEqualTo(1);
	}

	@Test
	void deletingUserRemovesAllPersonalData() {
		insertFood("라면");
		insertRecord("KEPT", "[]");
		jdbc.sql("INSERT INTO notification_settings (user_id) VALUES (:userId)").param("userId", userId).update();

		jdbc.sql("DELETE FROM users WHERE id = :userId").param("userId", userId).update();

		var remaining = jdbc.sql("""
				SELECT (SELECT count(*) FROM forbidden_foods WHERE user_id = :userId)
				     + (SELECT count(*) FROM daily_records WHERE user_id = :userId)
				     + (SELECT count(*) FROM notification_settings WHERE user_id = :userId)
				""")
				.param("userId", userId)
				.query(Long.class)
				.single();
		assertThat(remaining).isZero();
	}

	@Test
	void localDateUsesFourAmBoundary() {
		// 10/21 01:30 KST(= 10/20 16:30 UTC)의 야식은 10/20 기록이다.
		var localDate = jdbc.sql("""
				SELECT ((timestamptz '2026-10-20 16:30:00+00' AT TIME ZONE 'Asia/Seoul') - interval '4 hours')::date
				""")
				.query(LocalDate.class)
				.single();
		assertThat(localDate).isEqualTo(TODAY);
	}

	private void insertRecord(String status, String eatenFoods) {
		jdbc.sql("""
				INSERT INTO daily_records (user_id, local_date, status, forbidden_snapshot, eaten_foods)
				VALUES (:userId, :date, :status, :snapshot::jsonb, :eaten::jsonb)
				""")
				.param("userId", userId)
				.param("date", TODAY)
				.param("status", status)
				.param("snapshot", SNAPSHOT)
				.param("eaten", eatenFoods)
				.update();
	}

	private void upsertRecord(String status, String eatenFoods) {
		jdbc.sql("""
				INSERT INTO daily_records (user_id, local_date, status, forbidden_snapshot, eaten_foods)
				VALUES (:userId, :date, :status, :snapshot::jsonb, :eaten::jsonb)
				ON CONFLICT (user_id, local_date) DO UPDATE
				SET status = excluded.status,
				    forbidden_snapshot = excluded.forbidden_snapshot,
				    eaten_foods = excluded.eaten_foods,
				    updated_at = now()
				""")
				.param("userId", userId)
				.param("date", TODAY)
				.param("status", status)
				.param("snapshot", SNAPSHOT)
				.param("eaten", eatenFoods)
				.update();
	}

	private long insertFood(String name) {
		return jdbc.sql("INSERT INTO forbidden_foods (user_id, name) VALUES (:userId, :name) RETURNING id")
				.param("userId", userId)
				.param("name", name)
				.query(Long.class)
				.single();
	}
}
