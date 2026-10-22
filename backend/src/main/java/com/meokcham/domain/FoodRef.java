package com.meokcham.domain;

/**
 * 기록 스냅샷(jsonb)에 들어가는 음식 한 건. 기록 시점의 id와 이름을 그대로 보존한다.
 */
public record FoodRef(long id, String name) {
}
