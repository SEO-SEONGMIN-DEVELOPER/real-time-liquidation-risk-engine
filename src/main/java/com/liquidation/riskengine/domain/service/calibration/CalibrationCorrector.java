package com.liquidation.riskengine.domain.service.calibration;

import com.liquidation.riskengine.domain.model.CascadePredictionRecord;
import com.liquidation.riskengine.domain.model.McPredictionRecord;
import com.liquidation.riskengine.domain.repository.CascadePredictionRepository;
import com.liquidation.riskengine.domain.repository.McPredictionRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalibrationCorrector {

    static final int MIN_TOTAL_SAMPLES = 300;
    static final int MIN_BUCKET_SAMPLES = 30;
    static final int BUCKET_COUNT = 10;

    private final McPredictionRepository mcRepository;
    private final CascadePredictionRepository cascadeRepository;

    private volatile IsotonicModel mcModel;
    private volatile IsotonicModel cascadeModel;
    private volatile Instant lastFitTime;

    @Scheduled(cron = "0 0 4 * * *")
    public void dailyFit() {
        fitMcModel();
        fitCascadeModel();
        lastFitTime = Instant.now();
        log.info("[Calibration] 일일 보정 모델 재학습 완료: mc={}, cascade={}",
                mcModel != null ? "active" : "inactive",
                cascadeModel != null ? "active" : "inactive");
    }

    public void fitMcModel() {
        List<McPredictionRecord> records = mcRepository.findVerified(null, null);
        if (records.size() < MIN_TOTAL_SAMPLES) {
            mcModel = null;
            log.info("[Calibration] MC 데이터 부족: {}/{}", records.size(), MIN_TOTAL_SAMPLES);
            return;
        }

        double[] predicted = new double[records.size()];
        double[] actual = new double[records.size()];
        for (int i = 0; i < records.size(); i++) {
            predicted[i] = records.get(i).getPredictedProbability();
            actual[i] = Boolean.TRUE.equals(records.get(i).getActualHit()) ? 1.0 : 0.0;
        }

        mcModel = fitIsotonic(predicted, actual, "MC");
    }

    public void fitCascadeModel() {
        List<CascadePredictionRecord> records = cascadeRepository.findVerified(null);
        if (records.size() < MIN_TOTAL_SAMPLES) {
            cascadeModel = null;
            log.info("[Calibration] Cascade 데이터 부족: {}/{}", records.size(), MIN_TOTAL_SAMPLES);
            return;
        }

        double[] predicted = new double[records.size()];
        double[] actual = new double[records.size()];
        for (int i = 0; i < records.size(); i++) {
            predicted[i] = records.get(i).getReachProbability();
            actual[i] = Boolean.TRUE.equals(records.get(i).getActualHit()) ? 1.0 : 0.0;
        }

        cascadeModel = fitIsotonic(predicted, actual, "Cascade");
    }

    public double correctMc(double rawProb) {
        IsotonicModel model = mcModel;
        if (model == null) return rawProb;
        return model.predict(rawProb);
    }

    public double correctCascade(double rawProb) {
        IsotonicModel model = cascadeModel;
        if (model == null) return rawProb;
        return model.predict(rawProb);
    }

    public boolean isMcActive() {
        return mcModel != null;
    }

    public boolean isCascadeActive() {
        return cascadeModel != null;
    }

    public Instant getLastFitTime() {
        return lastFitTime;
    }

    public CalibrationStatus getStatus() {
        long mcTotal = mcRepository.countByVerifiedTrue();
        long cascadeTotal = cascadeRepository.countByVerifiedTrue();

        List<BucketCoefficient> mcCoeffs = mcModel != null ? mcModel.toBucketCoefficients() : List.of();
        List<BucketCoefficient> cascadeCoeffs = cascadeModel != null ? cascadeModel.toBucketCoefficients() : List.of();

        return CalibrationStatus.builder()
                .mcActive(isMcActive())
                .mcVerifiedSamples(mcTotal)
                .mcMinRequired(MIN_TOTAL_SAMPLES)
                .cascadeActive(isCascadeActive())
                .cascadeVerifiedSamples(cascadeTotal)
                .cascadeMinRequired(MIN_TOTAL_SAMPLES)
                .lastFitTime(lastFitTime)
                .mcBucketCoefficients(mcCoeffs)
                .cascadeBucketCoefficients(cascadeCoeffs)
                .build();
    }

    private IsotonicModel fitIsotonic(double[] predicted, double[] actual, String label) {
        int n = predicted.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(predicted[a], predicted[b]));

        double[] sortedPred = new double[n];
        double[] sortedActual = new double[n];
        for (int i = 0; i < n; i++) {
            sortedPred[i] = predicted[indices[i]];
            sortedActual[i] = actual[indices[i]];
        }

        double[] bucketPredSum = new double[BUCKET_COUNT];
        double[] bucketActualSum = new double[BUCKET_COUNT];
        int[] bucketCount = new int[BUCKET_COUNT];

        for (int i = 0; i < n; i++) {
            int b = Math.min((int) (sortedPred[i] * BUCKET_COUNT), BUCKET_COUNT - 1);
            bucketPredSum[b] += sortedPred[i];
            bucketActualSum[b] += sortedActual[i];
            bucketCount[b]++;
        }

        double[] x = new double[BUCKET_COUNT];
        double[] y = new double[BUCKET_COUNT];
        boolean[] valid = new boolean[BUCKET_COUNT];
        int validCount = 0;

        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (bucketCount[i] >= MIN_BUCKET_SAMPLES) {
                x[i] = bucketPredSum[i] / bucketCount[i];
                y[i] = bucketActualSum[i] / bucketCount[i];
                valid[i] = true;
                validCount++;
            }
        }

        if (validCount < 3) {
            log.info("[Calibration] {} 유효 버킷 부족: {}/3", label, validCount);
            return null;
        }

        double[] compactX = new double[validCount];
        double[] compactY = new double[validCount];
        int idx = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (valid[i]) {
                compactX[idx] = x[i];
                compactY[idx] = y[i];
                idx++;
            }
        }

        poolAdjacentViolators(compactY);

        log.info("[Calibration] {} Isotonic 피팅 완료: points={}, samples={}", label, validCount, n);
        return new IsotonicModel(compactX, compactY);
    }

    private void poolAdjacentViolators(double[] y) {
        int n = y.length;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n - 1; i++) {
                if (y[i] > y[i + 1]) {
                    double avg = (y[i] + y[i + 1]) / 2.0;
                    y[i] = avg;
                    y[i + 1] = avg;
                    changed = true;
                }
            }
        }
    }

    static class IsotonicModel {
        private final double[] x;
        private final double[] y;

        IsotonicModel(double[] x, double[] y) {
            this.x = x;
            this.y = y;
        }

        double predict(double rawProb) {
            if (x.length == 0) return rawProb;
            if (rawProb <= x[0]) return y[0];
            if (rawProb >= x[x.length - 1]) return y[y.length - 1];

            for (int i = 0; i < x.length - 1; i++) {
                if (rawProb >= x[i] && rawProb <= x[i + 1]) {
                    double t = (rawProb - x[i]) / (x[i + 1] - x[i]);
                    return y[i] + t * (y[i + 1] - y[i]);
                }
            }
            return rawProb;
        }

        List<BucketCoefficient> toBucketCoefficients() {
            List<BucketCoefficient> list = new ArrayList<>(x.length);
            for (int i = 0; i < x.length; i++) {
                list.add(BucketCoefficient.builder()
                        .predictedMean(x[i])
                        .correctedValue(y[i])
                        .build());
            }
            return list;
        }
    }

    @Getter
    @Builder
    public static class CalibrationStatus {
        private final boolean mcActive;
        private final long mcVerifiedSamples;
        private final int mcMinRequired;
        private final boolean cascadeActive;
        private final long cascadeVerifiedSamples;
        private final int cascadeMinRequired;
        private final Instant lastFitTime;
        private final List<BucketCoefficient> mcBucketCoefficients;
        private final List<BucketCoefficient> cascadeBucketCoefficients;
    }

    @Getter
    @Builder
    public static class BucketCoefficient {
        private final double predictedMean;
        private final double correctedValue;
    }
}
