package com.liquidation.riskengine.domain.service.cascade;

import com.liquidation.riskengine.domain.model.CascadePredictionRecord;
import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.repository.CascadePredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeCalibrationLogger {

    private final CascadePredictionRepository repository;

    @Value("${cascade.calibration.horizon-minutes:60}")
    private int verificationHorizonMinutes;

    @Value("${cascade.calibration.log-interval-seconds:60}")
    private long logIntervalSeconds;

    private final Map<String, Long> lastLogTimeBySymbol = new ConcurrentHashMap<>();

    public void logPrediction(CascadeRiskReport report) {
        if (report == null || report.getSymbol() == null) return;

        String symbol = report.getSymbol().toUpperCase();
        long now = System.currentTimeMillis();

        Long lastLog = lastLogTimeBySymbol.get(symbol);
        if (lastLog != null && (now - lastLog) < logIntervalSeconds * 1_000L) {
            return;
        }

        CascadePredictionRecord record = CascadePredictionRecord.builder()
                .symbol(symbol)
                .positionSide(report.getPositionSide())
                .reachProbability(report.getCascadeReachProbability() / 100.0)
                .distancePercent(report.getDistancePercent())
                .densityScore(report.getDensityScore())
                .marketPressureTotal(report.getMarketPressureTotal())
                .priceAtPrediction(report.getCurrentPrice().doubleValue())
                .liquidationPrice(report.getUserLiquidationPrice().doubleValue())
                .predictionEpochMs(now)
                .deadlineEpochMs(now + (long) verificationHorizonMinutes * 60_000L)
                .verified(false)
                .build();

        repository.save(record);
        lastLogTimeBySymbol.put(symbol, now);
        log.debug("[Cascade-Cal] 예측 기록: symbol={}, reachProb={:.1f}%, horizon={}min",
                symbol, report.getCascadeReachProbability(), verificationHorizonMinutes);
    }
}
