package com.liquidation.riskengine.domain.repository;

public interface CalibrationBucketRow {
    double getMeanPredicted();
    double getActualHitRate();
    long getSampleCount();
}
