package com.liquidation.riskengine.domain.repository;

import com.liquidation.riskengine.domain.model.FeedbackRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<FeedbackRecord, Long> {

    Page<FeedbackRecord> findAllByOrderByCreatedAtEpochMsDesc(Pageable pageable);
}
