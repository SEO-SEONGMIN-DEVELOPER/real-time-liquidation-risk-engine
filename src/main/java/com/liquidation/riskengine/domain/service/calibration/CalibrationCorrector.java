package com.liquidation.riskengine.domain.service.calibration;

import com.liquidation.riskengine.domain.repository.CalibrationBucketRow;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalibrationCorrector {

    static final int MIN_TOTAL_SAMPLES = 300;
    static final int MIN_BUCKET_SAMPLES = 30;

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
        List<CalibrationBucketRow> buckets = mcRepository.findBucketedForCalibration(null, null);
        long totalSamples = buckets.stream().mapToLong(CalibrationBucketRow::getSampleCount).sum();
        if (totalSamples < MIN_TOTAL_SAMPLES) {
            mcModel = null;
            log.info("[Calibration] MC 데이터 부족: {}/{}", totalSamples, MIN_TOTAL_SAMPLES);
            return;
        }
        mcModel = fitIsotonicFromBuckets(buckets, "MC");
    }

    public void fitCascadeModel() {
        List<CalibrationBucketRow> buckets = cascadeRepository.findBucketedForCalibration(null);
        long totalSamples = buckets.stream().mapToLong(CalibrationBucketRow::getSampleCount).sum();
        if (totalSamples < MIN_TOTAL_SAMPLES) {
            cascadeModel = null;
            log.info("[Calibration] Cascade 데이터 부족: {}/{}", totalSamples, MIN_TOTAL_SAMPLES);
            return;
        }
        cascadeModel = fitIsotonicFromBuckets(buckets, "Cascade");
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

    private IsotonicModel fitIsotonicFromBuckets(List<CalibrationBucketRow> buckets, String label) {
        List<CalibrationBucketRow> valid = buckets.stream()
                .filter(b -> b.getSampleCount() >= MIN_BUCKET_SAMPLES)
                .toList();

        if (valid.size() < 3) {
            log.info("[Calibration] {} 유효 버킷 부족: {}/3", label, valid.size());
            return null;
        }

        double[] x = new double[valid.size()];
        double[] y = new double[valid.size()];
        for (int i = 0; i < valid.size(); i++) {
            x[i] = valid.get(i).getMeanPredicted();
            y[i] = valid.get(i).getActualHitRate();
        }

        poolAdjacentViolators(y);

        long totalSamples = valid.stream().mapToLong(CalibrationBucketRow::getSampleCount).sum();
        log.info("[Calibration] {} Isotonic 피팅 완료: points={}, samples={}", label, valid.size(), totalSamples);
        return new IsotonicModel(x, y);
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
