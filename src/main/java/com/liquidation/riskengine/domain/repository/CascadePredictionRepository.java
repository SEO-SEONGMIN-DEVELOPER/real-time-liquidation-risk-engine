package com.liquidation.riskengine.domain.repository;

import com.liquidation.riskengine.domain.model.CascadePredictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CascadePredictionRepository extends JpaRepository<CascadePredictionRecord, Long> {

    List<CascadePredictionRecord> findByVerifiedFalseAndDeadlineEpochMsLessThanEqual(long now);

    @Query("SELECT c FROM CascadePredictionRecord c WHERE c.verified = true " +
            "AND (:symbol IS NULL OR c.symbol = :symbol)")
    List<CascadePredictionRecord> findVerified(@Param("symbol") String symbol);

    long countByVerifiedTrue();
}
