ALTER TABLE mc_prediction_record RENAME TO mc_prediction_record_backup;

CREATE SEQUENCE IF NOT EXISTS mc_prediction_record_id_seq
    START WITH 1 INCREMENT BY 1;

CREATE TABLE mc_prediction_record (
    id                       BIGINT       NOT NULL DEFAULT nextval('mc_prediction_record_id_seq'),
    symbol                   VARCHAR(255),
    horizon_minutes          INTEGER      NOT NULL,
    predicted_probability    DOUBLE PRECISION NOT NULL,
    price_at_prediction      DOUBLE PRECISION NOT NULL,
    liquidation_price        DOUBLE PRECISION NOT NULL,
    position_side            VARCHAR(255),
    sigma                    DOUBLE PRECISION NOT NULL,
    prediction_epoch_ms      BIGINT       NOT NULL,
    deadline_epoch_ms        BIGINT       NOT NULL,
    verified                 BOOLEAN      NOT NULL,
    actual_hit               BOOLEAN,
    price_at_deadline        DOUBLE PRECISION,
    price_min_during_horizon DOUBLE PRECISION,
    price_max_during_horizon DOUBLE PRECISION,
    verified_epoch_ms        BIGINT,
    PRIMARY KEY (id, prediction_epoch_ms)
) PARTITION BY RANGE (prediction_epoch_ms);

CREATE TABLE mc_prediction_record_2025_01 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1735689600000) TO (1738368000000);
CREATE TABLE mc_prediction_record_2025_02 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1738368000000) TO (1740787200000);
CREATE TABLE mc_prediction_record_2025_03 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1740787200000) TO (1743465600000);
CREATE TABLE mc_prediction_record_2025_04 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1743465600000) TO (1746057600000);
CREATE TABLE mc_prediction_record_2025_05 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1746057600000) TO (1748736000000);
CREATE TABLE mc_prediction_record_2025_06 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1748736000000) TO (1751328000000);
CREATE TABLE mc_prediction_record_2025_07 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1751328000000) TO (1754006400000);
CREATE TABLE mc_prediction_record_2025_08 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1754006400000) TO (1756684800000);
CREATE TABLE mc_prediction_record_2025_09 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1756684800000) TO (1759276800000);
CREATE TABLE mc_prediction_record_2025_10 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1759276800000) TO (1761955200000);
CREATE TABLE mc_prediction_record_2025_11 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1761955200000) TO (1764547200000);
CREATE TABLE mc_prediction_record_2025_12 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1764547200000) TO (1767225600000);

CREATE TABLE mc_prediction_record_2026_01 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1767225600000) TO (1769904000000);
CREATE TABLE mc_prediction_record_2026_02 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1769904000000) TO (1772323200000);
CREATE TABLE mc_prediction_record_2026_03 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1772323200000) TO (1775001600000);
CREATE TABLE mc_prediction_record_2026_04 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1775001600000) TO (1777593600000);
CREATE TABLE mc_prediction_record_2026_05 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1777593600000) TO (1780272000000);
CREATE TABLE mc_prediction_record_2026_06 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1780272000000) TO (1782864000000);
CREATE TABLE mc_prediction_record_2026_07 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1782864000000) TO (1785542400000);
CREATE TABLE mc_prediction_record_2026_08 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1785542400000) TO (1788220800000);
CREATE TABLE mc_prediction_record_2026_09 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1788220800000) TO (1790812800000);
CREATE TABLE mc_prediction_record_2026_10 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1790812800000) TO (1793491200000);
CREATE TABLE mc_prediction_record_2026_11 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1793491200000) TO (1796083200000);
CREATE TABLE mc_prediction_record_2026_12 PARTITION OF mc_prediction_record
    FOR VALUES FROM (1796083200000) TO (1798761600000);

CREATE TABLE mc_prediction_record_default PARTITION OF mc_prediction_record DEFAULT;

INSERT INTO mc_prediction_record SELECT * FROM mc_prediction_record_backup;
DROP TABLE mc_prediction_record_backup;

CREATE INDEX idx_mc_pred_symbol_verified
    ON mc_prediction_record (symbol, verified);

CREATE INDEX idx_mc_pred_deadline
    ON mc_prediction_record (deadline_epoch_ms, verified);

CREATE INDEX idx_mc_pred_calibration
    ON mc_prediction_record (verified, symbol, horizon_minutes)
    INCLUDE (predicted_probability, actual_hit);

CREATE INDEX idx_mc_pred_unverified_deadline
    ON mc_prediction_record (deadline_epoch_ms)
    WHERE verified = false;

CREATE INDEX idx_mc_pred_symbol_verified_epoch
    ON mc_prediction_record (symbol, verified, verified_epoch_ms DESC);


ALTER TABLE cascade_prediction_record RENAME TO cascade_prediction_record_backup;

CREATE SEQUENCE IF NOT EXISTS cascade_prediction_record_id_seq
    START WITH 1 INCREMENT BY 1;

CREATE TABLE cascade_prediction_record (
    id                       BIGINT       NOT NULL DEFAULT nextval('cascade_prediction_record_id_seq'),
    symbol                   VARCHAR(255),
    position_side            VARCHAR(255),
    reach_probability        DOUBLE PRECISION NOT NULL,
    distance_percent         DOUBLE PRECISION NOT NULL,
    density_score            DOUBLE PRECISION NOT NULL,
    market_pressure_total    INTEGER      NOT NULL,
    price_at_prediction      DOUBLE PRECISION NOT NULL,
    liquidation_price        DOUBLE PRECISION NOT NULL,
    prediction_epoch_ms      BIGINT       NOT NULL,
    deadline_epoch_ms        BIGINT       NOT NULL,
    verified                 BOOLEAN      NOT NULL,
    actual_hit               BOOLEAN,
    price_at_deadline        DOUBLE PRECISION,
    price_min_during_horizon DOUBLE PRECISION,
    price_max_during_horizon DOUBLE PRECISION,
    verified_epoch_ms        BIGINT,
    PRIMARY KEY (id, prediction_epoch_ms)
) PARTITION BY RANGE (prediction_epoch_ms);

CREATE TABLE cascade_prediction_record_2025_01 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1735689600000) TO (1738368000000);
CREATE TABLE cascade_prediction_record_2025_02 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1738368000000) TO (1740787200000);
CREATE TABLE cascade_prediction_record_2025_03 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1740787200000) TO (1743465600000);
CREATE TABLE cascade_prediction_record_2025_04 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1743465600000) TO (1746057600000);
CREATE TABLE cascade_prediction_record_2025_05 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1746057600000) TO (1748736000000);
CREATE TABLE cascade_prediction_record_2025_06 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1748736000000) TO (1751328000000);
CREATE TABLE cascade_prediction_record_2025_07 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1751328000000) TO (1754006400000);
CREATE TABLE cascade_prediction_record_2025_08 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1754006400000) TO (1756684800000);
CREATE TABLE cascade_prediction_record_2025_09 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1756684800000) TO (1759276800000);
CREATE TABLE cascade_prediction_record_2025_10 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1759276800000) TO (1761955200000);
CREATE TABLE cascade_prediction_record_2025_11 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1761955200000) TO (1764547200000);
CREATE TABLE cascade_prediction_record_2025_12 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1764547200000) TO (1767225600000);

CREATE TABLE cascade_prediction_record_2026_01 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1767225600000) TO (1769904000000);
CREATE TABLE cascade_prediction_record_2026_02 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1769904000000) TO (1772323200000);
CREATE TABLE cascade_prediction_record_2026_03 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1772323200000) TO (1775001600000);
CREATE TABLE cascade_prediction_record_2026_04 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1775001600000) TO (1777593600000);
CREATE TABLE cascade_prediction_record_2026_05 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1777593600000) TO (1780272000000);
CREATE TABLE cascade_prediction_record_2026_06 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1780272000000) TO (1782864000000);
CREATE TABLE cascade_prediction_record_2026_07 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1782864000000) TO (1785542400000);
CREATE TABLE cascade_prediction_record_2026_08 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1785542400000) TO (1788220800000);
CREATE TABLE cascade_prediction_record_2026_09 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1788220800000) TO (1790812800000);
CREATE TABLE cascade_prediction_record_2026_10 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1790812800000) TO (1793491200000);
CREATE TABLE cascade_prediction_record_2026_11 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1793491200000) TO (1796083200000);
CREATE TABLE cascade_prediction_record_2026_12 PARTITION OF cascade_prediction_record
    FOR VALUES FROM (1796083200000) TO (1798761600000);

CREATE TABLE cascade_prediction_record_default PARTITION OF cascade_prediction_record DEFAULT;

INSERT INTO cascade_prediction_record SELECT * FROM cascade_prediction_record_backup;
DROP TABLE cascade_prediction_record_backup;

CREATE INDEX idx_cascade_pred_symbol_verified
    ON cascade_prediction_record (symbol, verified);

CREATE INDEX idx_cascade_pred_deadline
    ON cascade_prediction_record (deadline_epoch_ms, verified);

CREATE INDEX idx_cascade_pred_calibration
    ON cascade_prediction_record (verified, symbol)
    INCLUDE (reach_probability, actual_hit);

CREATE INDEX idx_cascade_pred_unverified_deadline
    ON cascade_prediction_record (deadline_epoch_ms)
    WHERE verified = false;
