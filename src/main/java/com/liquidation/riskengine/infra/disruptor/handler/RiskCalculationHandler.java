package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.UserPosition;
import com.liquidation.riskengine.domain.service.CascadeRiskCalculator;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
import com.liquidation.riskengine.domain.service.RiskStateManager;
import com.liquidation.riskengine.infra.disruptor.event.EventType;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskCalculationHandler implements EventHandler<MarketDataEvent> {

    private static final long THROTTLE_INTERVAL_NS = 200_000_000L;

    private final CascadeRiskCalculator cascadeRiskCalculator;
    private final RiskStateManager riskStateManager;
    private final MarkPriceCache markPriceCache;
    private final RingBuffer<RiskResultEvent> riskResultRingBuffer;
    private final MeterRegistry meterRegistry;

    private final Map<String, Long> lastCalcTimeBySymbol = new ConcurrentHashMap<>();

    private Timer riskCalcTimer;
    private Timer e2eLatencyTimer;
    private Counter processedCounter;
    private Counter throttledCounter;

    @PostConstruct
    void initMetrics() {
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
    }

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) {
        if (event.getType() == null || !event.getType().triggersRiskCalc()) {
            return;
        }

        String symbol = event.getSymbol();
        if (symbol == null) return;

        if (event.getType() == EventType.MARK_PRICE) {
            long now = System.nanoTime();
            Long lastCalc = lastCalcTimeBySymbol.get(symbol);
            if (lastCalc != null && (now - lastCalc) < THROTTLE_INTERVAL_NS) {
                throttledCounter.increment();
                return;
            }
        }

        UserPosition position = riskStateManager.getPosition(symbol);
        if (position == null) return;

        BigDecimal currentPrice = markPriceCache.get(symbol);
        if (currentPrice == null) return;

        try {
            long startNano = System.nanoTime();

            CascadeRiskReport report = cascadeRiskCalculator.fullAnalysis(
                    currentPrice,
                    position.getLiquidationPrice(),
                    position.getPositionSide(),
                    symbol,
                    riskStateManager);

            long calcElapsed = System.nanoTime() - startNano;
            lastCalcTimeBySymbol.put(symbol, System.nanoTime());

            long e2eLatency = System.nanoTime() - event.getIngestNanoTime();

            riskCalcTimer.record(calcElapsed, TimeUnit.NANOSECONDS);
            e2eLatencyTimer.record(e2eLatency, TimeUnit.NANOSECONDS);
            processedCounter.increment();

            riskResultRingBuffer.publishEvent((resultEvent, seq) -> {
                resultEvent.clear();
                resultEvent.setReport(report);
                resultEvent.setCalcNanoTime(calcElapsed);
            });

            log.debug("[RiskCalc] {} | risk={} | reachProb={}% | calc={}μs | e2e={}μs",
                    symbol, report.getRiskLevel(),
                    String.format("%.1f", report.getCascadeReachProbability()),
                    calcElapsed / 1_000, e2eLatency / 1_000);
        } catch (Exception e) {
            log.error("[RiskCalc] 위험 계산 실패: symbol={}", symbol, e);
        }
    }
}
