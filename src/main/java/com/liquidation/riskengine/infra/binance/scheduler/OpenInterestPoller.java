package com.liquidation.riskengine.infra.binance.scheduler;

import com.lmax.disruptor.RingBuffer;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.infra.binance.client.BinanceRestClient;
import com.liquidation.riskengine.infra.binance.config.BinanceProperties;
import com.liquidation.riskengine.infra.disruptor.event.EventType;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenInterestPoller {

    private static final double BACKPRESSURE_THRESHOLD = 0.9;

    private final BinanceRestClient restClient;
    private final BinanceProperties properties;
    private final RingBuffer<MarketDataEvent> marketDataRingBuffer;
    private final MeterRegistry meterRegistry;

    private final Map<String, BigDecimal> previousOiMap = new ConcurrentHashMap<>();

    private Counter backpressureDropCounter;

    @PostConstruct
    void initMetrics() {
        backpressureDropCounter = Counter.builder("disruptor.events.dropped")
                .tag("reason", "backpressure")
                .tag("source", "oi-poller")
                .description("OI events dropped due to RingBuffer backpressure")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${binance.open-interest-poll-interval-ms:3000}")
    public void pollOpenInterest() {
        if (isBackpressureActive()) {
            backpressureDropCounter.increment();
            log.warn("[OI Poll] 백프레셔 활성 - OI 폴링 스킵 (util={}%)",
                    String.format("%.1f", getRingBufferUtilization() * 100));
            return;
        }

        for (String symbol : properties.getSymbols()) {
            restClient.getOpenInterest(symbol).ifPresent(response -> {
                String upperSymbol = symbol.toUpperCase();
                BigDecimal currentOi = response.getOpenInterest();
                BigDecimal previousOi = previousOiMap.get(upperSymbol);

                BigDecimal change = BigDecimal.ZERO;
                BigDecimal changePercent = BigDecimal.ZERO;

                if (previousOi != null && previousOi.compareTo(BigDecimal.ZERO) > 0) {
                    change = currentOi.subtract(previousOi);
                    changePercent = change.divide(previousOi, new MathContext(6, RoundingMode.HALF_UP))
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }

                OpenInterestSnapshot snapshot = OpenInterestSnapshot.builder()
                        .symbol(upperSymbol)
                        .openInterest(currentOi)
                        .previousOpenInterest(previousOi)
                        .change(change)
                        .changePercent(changePercent)
                        .timestamp(Instant.now().toEpochMilli())
                        .build();

                marketDataRingBuffer.publishEvent((event, sequence) -> {
                    event.clear();
                    event.setType(EventType.OI_UPDATE);
                    event.setSymbol(upperSymbol);
                    event.setOpenInterest(snapshot);
                    event.setIngestNanoTime(System.nanoTime());
                });

                previousOiMap.put(upperSymbol, currentOi);

                log.info("[OI Poll] symbol={}, oi={}, change={}, changePercent={}% → RingBuffer publish 완료",
                        upperSymbol, currentOi, change, changePercent);
            });
        }
    }

    private boolean isBackpressureActive() {
        return getRingBufferUtilization() > BACKPRESSURE_THRESHOLD;
    }

    private double getRingBufferUtilization() {
        return 1.0 - ((double) marketDataRingBuffer.remainingCapacity() / marketDataRingBuffer.getBufferSize());
    }
}
