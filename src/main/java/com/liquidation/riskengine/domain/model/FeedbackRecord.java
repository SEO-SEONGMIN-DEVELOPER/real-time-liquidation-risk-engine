package com.liquidation.riskengine.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "feedback_record", indexes = {
        @Index(name = "idx_feedback_created_at", columnList = "createdAtEpochMs"),
        @Index(name = "idx_feedback_symbol", columnList = "symbol")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String message;
    private String lang;
    private String symbol;
    private String ipAddress;
    private long createdAtEpochMs;
}
