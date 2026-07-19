CREATE TABLE IF NOT EXISTS user_binding (
    open_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_binding_user_id ON user_binding (user_id);

CREATE TABLE IF NOT EXISTS user_subscription (
    user_id VARCHAR(64) PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS user_usage_quota (
    user_id VARCHAR(64) NOT NULL,
    quota_type VARCHAR(64) NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    limit_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, quota_type)
);

CREATE TABLE IF NOT EXISTS essay_record (
    id VARCHAR(64) PRIMARY KEY,
    client_request_id VARCHAR(64) NULL,
    user_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    essay_type VARCHAR(32) NOT NULL,
    band VARCHAR(32) NOT NULL,
    band_label VARCHAR(32) NOT NULL,
    band_value VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    content TEXT NOT NULL,
    word_count INT NOT NULL DEFAULT 0,
    score_text VARCHAR(128) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    source VARCHAR(64) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    prompt_snapshot_json TEXT NOT NULL,
    coach_plan_json TEXT NULL,
    analysis_json TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_essay_record_user_created ON essay_record (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_essay_record_open_created ON essay_record (open_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_essay_record_user_request ON essay_record (user_id, client_request_id);

CREATE TABLE IF NOT EXISTS coach_template (
    id VARCHAR(64) PRIMARY KEY,
    essay_type VARCHAR(32) NOT NULL,
    scenario VARCHAR(64) NOT NULL,
    task_purpose VARCHAR(64) NOT NULL,
    official_logic TEXT NOT NULL,
    opening_strategy TEXT NOT NULL,
    body_strategy TEXT NOT NULL,
    ending_strategy TEXT NOT NULL,
    must_include_json TEXT NOT NULL,
    risk_points_json TEXT NOT NULL,
    useful_expressions_json TEXT NOT NULL,
    trigger_keywords_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_coach_template_type_enabled ON coach_template (essay_type, enabled, sort_order);

CREATE TABLE IF NOT EXISTS payment_order (
    out_trade_no VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    amount_fen INT NOT NULL,
    currency VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(255) NOT NULL,
    prepay_id VARCHAR(128) NULL,
    transaction_id VARCHAR(128) NULL,
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    payload_json TEXT NULL,
    paid_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_order_order_id ON payment_order (order_id);
CREATE INDEX IF NOT EXISTS idx_payment_order_user_created ON payment_order (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_order_transaction ON payment_order (transaction_id);
