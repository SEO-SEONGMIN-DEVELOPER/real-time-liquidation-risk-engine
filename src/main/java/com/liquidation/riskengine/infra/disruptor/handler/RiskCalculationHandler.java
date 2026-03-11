package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.MonteCarloReport;
import com.liquidation.riskengine.domain.model.UserPosition;
import com.liquidation.riskengine.domain.service.CascadeRiskCalculator;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
import com.liquidation.riskengine.domain.service.RiskStateManager;
import com.liquidation.riskengine.domain.service.cascade.CascadeCalibrationLogger;
import com.liquidation.riskengine.domain.service.montecarlo.MonteCarloCalibrationLogger;
import com.liquidation.riskengine.domain.service.montecarlo.MonteCarloProperties;
import com.liquidation.riskengine.domain.service.montecarlo.MonteCarloSimulationService;
import com.liquidation.riskengine.infra.disruptor.event.EventType;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskCalculationHandler implements EventHandler<MarketDataEvent> {

    private final CascadeRiskCalculator cascadeRiskCalculator;
    private final RiskStateManager riskStateManager;
    private final MarkPriceCache markPriceCache;
    private final RingBuffer<RiskResultEvent> riskResultRingBuffer;
    private final MeterRegistry meterRegistry;
    private final MonteCarloSimulationService mcService;
    private final MonteCarloProperties mcProperties;
    private final MonteCarloCalibrationLogger calibrationLogger;
    private final CascadeCalibrationLogger cascadeCalibrationLogger;

    @Value("${risk.cascade.throttle-ms:200}")
    private long cascadeThrottleMs;

    private long throttleIntervalNs;

    private final Map<String, Long> lastCalcTimeBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Long> lastMcCalcTimeByUserAndSymbol = new ConcurrentHashMap<>();
    private final Map<String, CascadeRiskReport> latestCascadeReportsByUserAndSymbol = new ConcurrentHashMap<>();

    private Timer riskCalcTimer;
    private Timer e2eLatencyTimer;
    private Counter processedCounter;
    private Counter throttledCounter;
    private Counter mcProcessedCounter;

    @PostConstruct
    void initMetrics() {
        throttleIntervalNs = cascadeThrottleMs * 1_000_000L;
        log.info("[RiskCalc] Cascade 스로틀 간격: {}ms", cascadeThrottleMs);
        riskCalcTimer = Timer.builder("disruptor.risk.calc_duration")
                .description("5-stage risk calculation duration")
                .register(meterRegistry);
        e2eLatencyTimer = Timer.builder("disruptor.event.e2e_latency")
                .description("End-to-end event latency (ingest → risk calc complete)")
                .register(meterRegistry);
        processedCounter = Counter.builder("disruptor.events.processed")
                .description("Events successfully processed through risk calculation")
                .register(meterRegistry);
        throttledCounter = Counter.builder("disruptor.events.throttled")
                .description("MARK_PRICE events skipped by 200ms throttle")
                .register(meterRegistry);
        mcProcessedCounter = Counter.builder("disruptor.mc.processed")
                .description("Monte Carlo simulations completed")
                .register(meterRegistry);
    }

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) {
        if (event.getType() == null || !event.getType().triggersRiskCalc()) {
            return;
        }

        String symbol = event.getSymbol();
        if (symbol == null) return;

        Collection<UserPosition> positions = riskStateManager.getPositionsBySymbol(symbol);
        if (positions.isEmpty()) return;

        long now = System.nanoTime();

        if (event.getType() == EventType.MARK_PRICE) {
            Long lastCalc = lastCalcTimeBySymbol.get(symbol);
            if (lastCalc != null && (now - lastCalc) < throttleIntervalNs) {
                throttledCounter.increment();
                for (UserPosition position : positions) {
                    if (position.getUserId() == null || position.getUserId().isBlank()) continue;
                    tryMonteCarlo(normalizeUserId(position.getUserId()), symbol, now, position);
                }
                return;
            }
        }

        BigDecimal currentPrice = markPriceCache.get(symbol);
        if (currentPrice == null) return;

        for (UserPosition position : positions) {
            if (position.getLiquidationPrice() == null || position.getUserId() == null || position.getUserId().isBlank()) {
                continue;
            }

            String userId = normalizeUserId(position.getUserId());
            String userSymbolKey = userSymbolKey(userId, symbol);

            try {
                long startNano = System.nanoTime();

                CascadeRiskReport report = cascadeRiskCalculator.fullAnalysis(
                        currentPrice,
                        position.getLiquidationPrice(),
                        position.getPositionSide(),
                        symbol,
                        riskStateManager);
                report.setUserId(userId);

                long calcElapsed = System.nanoTime() - startNano;
                lastCalcTimeBySymbol.put(symbol, System.nanoTime());

                long e2eLatency = System.nanoTime() - event.getIngestNanoTime();

                riskCalcTimer.record(calcElapsed, TimeUnit.NANOSECONDS);
                e2eLatencyTimer.record(e2eLatency, TimeUnit.NANOSECONDS);
                processedCounter.increment();

                riskResultRingBuffer.publishEvent((resultEvent, seq) -> {
                    resultEvent.clear();
                    resultEvent.setUserId(userId);
                    resultEvent.setReport(report);
                    resultEvent.setCalcNanoTime(calcElapsed);
                });

                latestCascadeReportsByUserAndSymbol.put(userSymbolKey, report);

                try {
                    cascadeCalibrationLogger.logPrediction(report);
                } catch (Exception ex) {
                    log.warn("[Cascade] 캘리브레이션 기록 실패: userId={}, symbol={}", userId, symbol, ex);
                }

                log.debug("[RiskCalc] userId={}, symbol={} | risk={} | reachProb={}% | calc={}μs | e2e={}μs",
                        userId, symbol, report.getRiskLevel(),
                        String.format("%.1f", report.getCascadeReachProbability()),
                        calcElapsed / 1_000, e2eLatency / 1_000);
            } catch (Exception e) {
                log.error("[RiskCalc] 위험 계산 실패: userId={}, symbol={}", userId, symbol, e);
            }

            tryMonteCarlo(userId, symbol, now, position);
        }
    }

    private void tryMonteCarlo(String userId, String symbol, long nowNano, UserPosition position) {
        if (!mcProperties.isEnabled()) return;

        long throttleNs = mcProperties.getThrottleIntervalSeconds() * 1_000_000_000L;
        String userSymbolKey = userSymbolKey(userId, symbol);
        Long lastMc = lastMcCalcTimeByUserAndSymbol.get(userSymbolKey);
        if (lastMc != null && (nowNano - lastMc) < throttleNs) {
            return;
        }

        if (position == null || position.getLiquidationPrice() == null) return;

        try {
            CascadeRiskReport cascadeReport = latestCascadeReportsByUserAndSymbol.get(userSymbolKey);
            mcService.simulate(userId, symbol, position.getLiquidationPrice(), position.getPositionSide(), cascadeReport)
                    .ifPresent(mcReport -> {
                        lastMcCalcTimeByUserAndSymbol.put(userSymbolKey, System.nanoTime());
                        mcProcessedCounter.increment();

                        riskResultRingBuffer.publishEvent((resultEvent, seq) -> {
                            resultEvent.clear();
                            resultEvent.setUserId(userId);
                            resultEvent.setMcReport(mcReport);
                        });

                        try {
                            calibrationLogger.logPrediction(mcReport);
                        } catch (Exception e) {
                            log.warn("[MC] 캘리브레이션 기록 실패: userId={}, symbol={}", userId, symbol, e);
                        }

                        log.info("[MC] userId={}, symbol={} | risk={} | 24h_prob={}% | σ={:.4f} | calc={}μs",
                                userId, symbol, mcReport.getRiskLevel(),
                                String.format("%.1f", get24hProbability(mcReport) * 100),
                                mcReport.getSigma(), mcReport.getCalcDurationMicros());
                    });
        } catch (Exception e) {
            log.error("[MC] 시뮬레이션 실패: userId={}, symbol={}", userId, symbol, e);
        }
    }

    private double get24hProbability(MonteCarloReport report) {
        return report.getHorizons().stream()
                .filter(h -> h.getMinutes() == 1440)
                .findFirst()
                .map(MonteCarloReport.HorizonResult::getLiquidationProbability)
                .orElse(0.0);
    }

    private String userSymbolKey(String userId, String symbol) {
        return normalizeUserId(userId) + "|" + symbol.toUpperCase();
    }

    private String normalizeUserId(String userId) {
        return userId.trim().toLowerCase();
    }
}
