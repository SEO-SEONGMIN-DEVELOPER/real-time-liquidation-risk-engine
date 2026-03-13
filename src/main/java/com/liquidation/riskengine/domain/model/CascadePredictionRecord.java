package com.liquidation.riskengine.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cascade_prediction_record", indexes = {
        @Index(name = "idx_cascade_pred_symbol_verified", columnList = "symbol, verified"),
        @Index(name = "idx_cascade_pred_deadline", columnList = "deadlineEpochMs, verified")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CascadePredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String positionSide;
    private double reachProbability;
    private double distancePercent;
    private double densityScore;
    private int marketPressureTotal;
    private double priceAtPrediction;
    private double liquidationPrice;

    private long predictionEpochMs;
    private long deadlineEpochMs;

    private boolean verified;
    private Boolean actualHit;
    private Double priceAtDeadline;
    private Double priceMinDuringHorizon;
    private Double priceMaxDuringHorizon;
    private Long verifiedEpochMs;
}
