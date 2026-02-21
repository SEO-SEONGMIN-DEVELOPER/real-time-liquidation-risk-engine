package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.McPredictionRecord;
import com.liquidation.riskengine.domain.model.MonteCarloReport;
import com.liquidation.riskengine.domain.repository.McPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonteCarloCalibrationLogger {

    private final McPredictionRepository repository;

    public void logPrediction(MonteCarloReport report) {
        if (report == null || report.getHorizons() == null) return;

        long now = System.currentTimeMillis();
        List<McPredictionRecord> records = new ArrayList<>(report.getHorizons().size());

        for (MonteCarloReport.HorizonResult h : report.getHorizons()) {
            records.add(McPredictionRecord.builder()
                    .symbol(report.getSymbol().toUpperCase())
                    .horizonMinutes(h.getMinutes())
                    .predictedProbability(h.getLiquidationProbability())
                    .priceAtPrediction(report.getCurrentPrice())
                    .liquidationPrice(report.getLiquidationPrice())
                    .positionSide(report.getPositionSide())
                    .sigma(report.getSigma())
                    .predictionEpochMs(now)
                    .deadlineEpochMs(now + (long) h.getMinutes() * 60_000L)
                    .verified(false)
                    .build());
        }

        repository.saveAll(records);
        log.debug("[Calibration] 예측 기록: symbol={}, horizons={}", report.getSymbol(), records.size());
    }
}
