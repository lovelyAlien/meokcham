-- 먹참 초기 스키마. 설계 근거는 docs/DB_SCHEMA.md 참고.

CREATE TABLE users (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    timezone   varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE social_accounts (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         varchar(16)  NOT NULL CHECK (provider IN ('APPLE', 'GOOGLE')),
    provider_subject varchar(255) NOT NULL,
    email            varchar(320),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_social_accounts_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_social_accounts_user_id ON social_accounts (user_id);

CREATE TABLE forbidden_foods (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       varchar(50) NOT NULL CHECK (length(trim(name)) > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

-- 삭제되지 않은 음식끼리만 이름이 겹치지 않게 한다. 지웠다가 같은 이름으로 다시 추가할 수 있다.
CREATE UNIQUE INDEX uk_forbidden_foods_active_name
    ON forbidden_foods (user_id, name)
    WHERE deleted_at IS NULL;

CREATE TABLE daily_records (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    local_date         date        NOT NULL,
    status             varchar(16) NOT NULL CHECK (status IN ('KEPT', 'BROKEN')),
    forbidden_snapshot jsonb       NOT NULL CHECK (jsonb_typeof(forbidden_snapshot) = 'array'),
    eaten_foods        jsonb       NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(eaten_foods) = 'array'),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_daily_records_user_date UNIQUE (user_id, local_date),
    -- 지킴이면 먹은 음식이 없고, 어김이면 하나 이상 있어야 한다.
    CONSTRAINT ck_daily_records_status_eaten CHECK (
        (status = 'KEPT' AND jsonb_array_length(eaten_foods) = 0)
        OR (status = 'BROKEN' AND jsonb_array_length(eaten_foods) > 0)
    )
);

CREATE TABLE notification_settings (
    user_id          bigint      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled          boolean     NOT NULL DEFAULT true,
    notify_time      time        NOT NULL DEFAULT '21:00',
    last_notified_on date,
    updated_at       timestamptz NOT NULL DEFAULT now()
);

-- 스케줄러가 매분 "알림을 켠 사용자 중 이 시각인 사용자"를 찾는다.
CREATE INDEX idx_notification_settings_time
    ON notification_settings (notify_time)
    WHERE enabled;

CREATE TABLE push_tokens (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token        varchar(255) NOT NULL,
    platform     varchar(16)  NOT NULL CHECK (platform IN ('IOS', 'ANDROID')),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    last_seen_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_push_tokens_token UNIQUE (token)
);

CREATE INDEX idx_push_tokens_user_id ON push_tokens (user_id);

CREATE TABLE refresh_tokens (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash char(64)    NOT NULL,
    family_id  uuid        NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
