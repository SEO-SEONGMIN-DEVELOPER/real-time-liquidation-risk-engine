package com.liquidation.riskengine.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "cascade_prediction_record")
@IdClass(CascadePredictionRecord.CascadePredictionKey.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CascadePredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cascade_pred_seq")
    @SequenceGenerator(name = "cascade_pred_seq", sequenceName = "cascade_prediction_record_id_seq", allocationSize = 50)
    private Long id;

    @Id
    private long predictionEpochMs;

    private String symbol;
    private String positionSide;
    private double reachProbability;
    private double distancePercent;
    private double densityScore;
    private int marketPressureTotal;
    private double priceAtPrediction;
    private double liquidationPrice;

    private long deadlineEpochMs;

    private boolean verified;
    private Boolean actualHit;
    private Double priceAtDeadline;
    private Double priceMinDuringHorizon;
    private Double priceMaxDuringHorizon;
    private Long verifiedEpochMs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CascadePredictionKey implements Serializable {
        private Long id;
        private long predictionEpochMs;
    }
}
