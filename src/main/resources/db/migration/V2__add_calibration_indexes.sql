CREATE INDEX IF NOT EXISTS idx_mc_pred_calibration
    ON mc_prediction_record (verified, symbol, horizon_minutes)
    INCLUDE (predicted_probability, actual_hit);

CREATE INDEX IF NOT EXISTS idx_mc_pred_unverified_deadline
    ON mc_prediction_record (deadline_epoch_ms)
    WHERE verified = false;

CREATE INDEX IF NOT EXISTS idx_cascade_pred_calibration
    ON cascade_prediction_record (verified, symbol)
    INCLUDE (reach_probability, actual_hit);

CREATE INDEX IF NOT EXISTS idx_cascade_pred_unverified_deadline
    ON cascade_prediction_record (deadline_epoch_ms)
    WHERE verified = false;
