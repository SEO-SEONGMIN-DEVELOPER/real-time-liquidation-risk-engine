package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.McPredictionRecord;
import com.liquidation.riskengine.domain.repository.McPredictionRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class McCalibrationMetrics {

    private static final int BUCKET_COUNT = 10;

    private final McPredictionRepository repository;

    public CalibrationReport calculate(String symbol, Integer horizon) {
        List<McPredictionRecord> records = repository.findVerified(symbol, horizon);

        if (records.isEmpty()) {
            return CalibrationReport.builder()
                    .totalSamples(0)
                    .calibrationCurve(List.of())
                    .build();
        }

        int[] bucketHits = new int[BUCKET_COUNT];
        int[] bucketCounts = new int[BUCKET_COUNT];
        double[] bucketProbSum = new double[BUCKET_COUNT];

        for (McPredictionRecord r : records) {
            double p = r.getPredictedProbability();
            int outcome = Boolean.TRUE.equals(r.getActualHit()) ? 1 : 0;

            int bucket = Math.min((int) (p * BUCKET_COUNT), BUCKET_COUNT - 1);
            bucketCounts[bucket]++;
            bucketHits[bucket] += outcome;
            bucketProbSum[bucket] += p;
        }

        List<CalibrationBucket> curve = new ArrayList<>(BUCKET_COUNT);
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (bucketCounts[i] == 0) continue;
            curve.add(CalibrationBucket.builder()
                    .bucketRangeStart(i * 0.1)
                    .bucketRangeEnd((i + 1) * 0.1)
                    .meanPredictedProb(bucketProbSum[i] / bucketCounts[i])
                    .actualHitRate((double) bucketHits[i] / bucketCounts[i])
                    .sampleCount(bucketCounts[i])
                    .build());
        }

        log.info("[Calibration] samples={}, buckets={}, symbol={}, horizon={}",
                records.size(), curve.size(), symbol, horizon);

        return CalibrationReport.builder()
                .totalSamples(records.size())
                .calibrationCurve(curve)
                .build();
    }

    @Getter
    @Builder
    public static class CalibrationReport {
        private final int totalSamples;
        private final List<CalibrationBucket> calibrationCurve;
    }

    @Getter
    @Builder
    public static class CalibrationBucket {
        private final double bucketRangeStart;
        private final double bucketRangeEnd;
        private final double meanPredictedProb;
        private final double actualHitRate;
        private final int sampleCount;
    }
}
