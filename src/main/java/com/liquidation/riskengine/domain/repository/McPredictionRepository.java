package com.liquidation.riskengine.domain.repository;

import com.liquidation.riskengine.domain.model.McPredictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface McPredictionRepository extends JpaRepository<McPredictionRecord, McPredictionRecord.McPredictionKey> {

    List<McPredictionRecord> findByVerifiedFalseAndDeadlineEpochMsLessThanEqual(long now);

    List<McPredictionRecord> findBySymbolAndVerifiedTrueOrderByVerifiedEpochMsDesc(String symbol);

    @Query(value = """
            SELECT
                AVG(predicted_probability)                                    AS mean_predicted,
                AVG(CASE WHEN actual_hit = true THEN 1.0 ELSE 0.0 END)       AS actual_hit_rate,
                COUNT(*)                                                      AS sample_count
            FROM mc_prediction_record
            WHERE verified = true
              AND (:symbol IS NULL OR symbol = :symbol)
              AND (:horizon IS NULL OR horizon_minutes = :horizon)
            GROUP BY FLOOR(predicted_probability * 10)
            ORDER BY FLOOR(predicted_probability * 10)
            """, nativeQuery = true)
    List<CalibrationBucketRow> findBucketedForCalibration(@Param("symbol") String symbol,
                                                          @Param("horizon") Integer horizon);

    long countByVerifiedTrue();
}
