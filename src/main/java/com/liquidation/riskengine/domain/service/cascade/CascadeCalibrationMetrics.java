package com.liquidation.riskengine.domain.service.cascade;

import com.liquidation.riskengine.domain.repository.CalibrationBucketRow;
import com.liquidation.riskengine.domain.repository.CascadePredictionRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeCalibrationMetrics {

    private final CascadePredictionRepository repository;

    public CalibrationReport calculate(String symbol) {
        List<CalibrationBucketRow> buckets = repository.findBucketedForCalibration(symbol);

        if (buckets.isEmpty()) {
            return CalibrationReport.builder()
                    .totalSamples(0)
                    .calibrationCurve(List.of())
                    .build();
        }

        long totalSamples = buckets.stream().mapToLong(CalibrationBucketRow::getSampleCount).sum();

        List<CalibrationBucket> curve = buckets.stream()
                .map(b -> {
                    double start = Math.floor(b.getMeanPredicted() * 10) / 10.0;
                    return CalibrationBucket.builder()
                            .bucketRangeStart(start)
                            .bucketRangeEnd(start + 0.1)
                            .meanPredictedProb(b.getMeanPredicted())
                            .actualHitRate(b.getActualHitRate())
                            .sampleCount((int) b.getSampleCount())
                            .build();
                })
                .toList();

        log.info("[Cascade-Cal] samples={}, buckets={}, symbol={}", totalSamples, curve.size(), symbol);

        return CalibrationReport.builder()
                .totalSamples((int) totalSamples)
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
