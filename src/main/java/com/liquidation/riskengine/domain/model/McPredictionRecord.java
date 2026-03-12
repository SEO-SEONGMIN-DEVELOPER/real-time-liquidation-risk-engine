package com.liquidation.riskengine.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mc_prediction_record", indexes = {
        @Index(name = "idx_mc_pred_symbol_verified", columnList = "symbol, verified"),
        @Index(name = "idx_mc_pred_deadline", columnList = "deadlineEpochMs, verified")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McPredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private int horizonMinutes;
    private double predictedProbability;
    private double priceAtPrediction;
    private double liquidationPrice;
    private String positionSide;
    private double sigma;

    private long predictionEpochMs;
    private long deadlineEpochMs;

    private boolean verified;
    private Boolean actualHit;
    private Double priceAtDeadline;
    private Double priceMinDuringHorizon;
    private Double priceMaxDuringHorizon;
    private Long verifiedEpochMs;
}
