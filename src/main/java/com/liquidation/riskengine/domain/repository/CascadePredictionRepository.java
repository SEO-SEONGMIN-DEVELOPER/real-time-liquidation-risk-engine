package com.liquidation.riskengine.domain.repository;

import com.liquidation.riskengine.domain.model.CascadePredictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CascadePredictionRepository extends JpaRepository<CascadePredictionRecord, CascadePredictionRecord.CascadePredictionKey> {

    List<CascadePredictionRecord> findByVerifiedFalseAndDeadlineEpochMsLessThanEqual(long now);

    @Query(value = """
            SELECT
                AVG(reach_probability)                                        AS mean_predicted,
                AVG(CASE WHEN actual_hit = true THEN 1.0 ELSE 0.0 END)       AS actual_hit_rate,
                COUNT(*)                                                      AS sample_count
            FROM cascade_prediction_record
            WHERE verified = true
              AND (:symbol IS NULL OR symbol = :symbol)
            GROUP BY FLOOR(reach_probability * 10)
            ORDER BY FLOOR(reach_probability * 10)
            """, nativeQuery = true)
    List<CalibrationBucketRow> findBucketedForCalibration(@Param("symbol") String symbol);

    long countByVerifiedTrue();
}
