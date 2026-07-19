CREATE TABLE IF NOT EXISTS user_binding (
    open_id VARCHAR(128) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_user_binding_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS user_subscription (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS user_usage_quota (
    user_id VARCHAR(64) NOT NULL,
    quota_type VARCHAR(64) NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    limit_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, quota_type)
);

CREATE TABLE IF NOT EXISTS essay_record (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    client_request_id VARCHAR(64) NULL,
    user_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    essay_type VARCHAR(32) NOT NULL,
    band VARCHAR(32) NOT NULL,
    band_label VARCHAR(32) NOT NULL,
    band_value VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    word_count INT NOT NULL DEFAULT 0,
    score_text VARCHAR(128) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    source VARCHAR(64) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    prompt_snapshot_json LONGTEXT NOT NULL,
    coach_plan_json LONGTEXT NULL,
    analysis_json LONGTEXT NULL,
    UNIQUE KEY uk_essay_record_user_request (user_id, client_request_id),
    KEY idx_essay_record_user_created (user_id, created_at),
    KEY idx_essay_record_open_created (open_id, created_at)
);

CREATE TABLE IF NOT EXISTS coach_template (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    essay_type VARCHAR(32) NOT NULL,
    scenario VARCHAR(64) NOT NULL,
    task_purpose VARCHAR(64) NOT NULL,
    official_logic TEXT NOT NULL,
    opening_strategy TEXT NOT NULL,
    body_strategy TEXT NOT NULL,
    ending_strategy TEXT NOT NULL,
    must_include_json LONGTEXT NOT NULL,
    risk_points_json LONGTEXT NOT NULL,
    useful_expressions_json LONGTEXT NOT NULL,
    trigger_keywords_json LONGTEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    KEY idx_coach_template_type_enabled (essay_type, enabled, sort_order)
);

CREATE TABLE IF NOT EXISTS payment_order (
    out_trade_no VARCHAR(64) NOT NULL PRIMARY KEY,
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
    payload_json LONGTEXT NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_payment_order_order_id (order_id),
    KEY idx_payment_order_user_created (user_id, created_at),
    KEY idx_payment_order_transaction (transaction_id)
);
