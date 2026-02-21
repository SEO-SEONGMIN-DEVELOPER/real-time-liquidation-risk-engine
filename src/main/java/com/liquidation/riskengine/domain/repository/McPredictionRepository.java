package com.liquidation.riskengine.domain.repository;

import com.liquidation.riskengine.domain.model.McPredictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface McPredictionRepository extends JpaRepository<McPredictionRecord, Long> {

    List<McPredictionRecord> findByVerifiedFalseAndDeadlineEpochMsLessThanEqual(long now);

    List<McPredictionRecord> findBySymbolAndVerifiedTrueOrderByVerifiedEpochMsDesc(String symbol);

    @Query("SELECT m FROM McPredictionRecord m WHERE m.verified = true " +
            "AND (:symbol IS NULL OR m.symbol = :symbol) " +
            "AND (:horizon IS NULL OR m.horizonMinutes = :horizon)")
    List<McPredictionRecord> findVerified(@Param("symbol") String symbol,
                                          @Param("horizon") Integer horizon);

    long countByVerifiedTrue();
}
