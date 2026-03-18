CREATE TABLE IF NOT EXISTS cascade_prediction_record (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(255),
    position_side VARCHAR(255),
    reach_probability DOUBLE PRECISION NOT NULL,
    distance_percent DOUBLE PRECISION NOT NULL,
    density_score DOUBLE PRECISION NOT NULL,
    market_pressure_total INTEGER NOT NULL,
    price_at_prediction DOUBLE PRECISION NOT NULL,
    liquidation_price DOUBLE PRECISION NOT NULL,
    prediction_epoch_ms BIGINT NOT NULL,
    deadline_epoch_ms BIGINT NOT NULL,
    verified BOOLEAN NOT NULL,
    actual_hit BOOLEAN,
    price_at_deadline DOUBLE PRECISION,
    price_min_during_horizon DOUBLE PRECISION,
    price_max_during_horizon DOUBLE PRECISION,
    verified_epoch_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cascade_pred_symbol_verified
    ON cascade_prediction_record (symbol, verified);

CREATE INDEX IF NOT EXISTS idx_cascade_pred_deadline
    ON cascade_prediction_record (deadline_epoch_ms, verified);

CREATE TABLE IF NOT EXISTS mc_prediction_record (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(255),
    horizon_minutes INTEGER NOT NULL,
    predicted_probability DOUBLE PRECISION NOT NULL,
    price_at_prediction DOUBLE PRECISION NOT NULL,
    liquidation_price DOUBLE PRECISION NOT NULL,
    position_side VARCHAR(255),
    sigma DOUBLE PRECISION NOT NULL,
    prediction_epoch_ms BIGINT NOT NULL,
    deadline_epoch_ms BIGINT NOT NULL,
    verified BOOLEAN NOT NULL,
    actual_hit BOOLEAN,
    price_at_deadline DOUBLE PRECISION,
    price_min_during_horizon DOUBLE PRECISION,
    price_max_during_horizon DOUBLE PRECISION,
    verified_epoch_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mc_pred_symbol_verified
    ON mc_prediction_record (symbol, verified);

CREATE INDEX IF NOT EXISTS idx_mc_pred_deadline
    ON mc_prediction_record (deadline_epoch_ms, verified);

CREATE TABLE IF NOT EXISTS feedback_record (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    message VARCHAR(2000),
    lang VARCHAR(32),
    symbol VARCHAR(64),
    ip_address VARCHAR(128),
    created_at_epoch_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_created_at
    ON feedback_record (created_at_epoch_ms);

CREATE INDEX IF NOT EXISTS idx_feedback_symbol
    ON feedback_record (symbol);
