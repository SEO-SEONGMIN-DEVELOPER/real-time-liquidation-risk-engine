package com.liquidation.riskengine.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "mc_prediction_record")
@IdClass(McPredictionRecord.McPredictionKey.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McPredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mc_pred_seq")
    @SequenceGenerator(name = "mc_pred_seq", sequenceName = "mc_prediction_record_id_seq", allocationSize = 50)
    private Long id;

    @Id
    private long predictionEpochMs;

    private String symbol;
    private int horizonMinutes;
    private double predictedProbability;
    private double priceAtPrediction;
    private double liquidationPrice;
    private String positionSide;
    private double sigma;

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
    public static class McPredictionKey implements Serializable {
        private Long id;
        private long predictionEpochMs;
    }
}
