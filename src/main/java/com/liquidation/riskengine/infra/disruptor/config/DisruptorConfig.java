package com.liquidation.riskengine.infra.disruptor.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEventFactory;
import com.liquidation.riskengine.infra.disruptor.handler.CacheUpdateHandler;
import com.liquidation.riskengine.infra.disruptor.handler.DisruptorExceptionHandler;
import com.liquidation.riskengine.infra.disruptor.handler.JournalEventHandler;
import com.liquidation.riskengine.infra.disruptor.handler.ParseEventHandler;
import com.liquidation.riskengine.infra.disruptor.handler.RiskCalculationHandler;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DisruptorConfig {

    private static final int INGEST_BUFFER_SIZE = 1024 * 64;

    private final ParseEventHandler parseEventHandler;
    private final JournalEventHandler journalEventHandler;
    private final CacheUpdateHandler cacheUpdateHandler;
    private final RiskCalculationHandler riskCalculationHandler;
    private final MeterRegistry meterRegistry;
    private final Environment environment;

    private Disruptor<MarketDataEvent> ingestDisruptor;

    @Bean
    public Disruptor<MarketDataEvent> marketDataDisruptor() {
        WaitStrategy waitStrategy = resolveWaitStrategy();

        ingestDisruptor = new Disruptor<>(
                new MarketDataEventFactory(),
                INGEST_BUFFER_SIZE,
                namedThreadFactory("disruptor-ingest"),
                ProducerType.SINGLE,
                waitStrategy
        );

        ingestDisruptor.setDefaultExceptionHandler(
                new DisruptorExceptionHandler<>("ingest", meterRegistry));

        ingestDisruptor
                .handleEventsWith(parseEventHandler)
                .then(journalEventHandler, cacheUpdateHandler);

        ingestDisruptor
                .after(cacheUpdateHandler)
                .then(riskCalculationHandler);

        ingestDisruptor.start();

        log.info("[Disruptor] Ingest 파이프라인 기동: Parse → (Journal || Cache → RiskCalc) | size={}, wait={}",
                INGEST_BUFFER_SIZE, waitStrategy.getClass().getSimpleName());

        return ingestDisruptor;
    }

    @Bean
    public RingBuffer<MarketDataEvent> marketDataRingBuffer(Disruptor<MarketDataEvent> marketDataDisruptor) {
        return marketDataDisruptor.getRingBuffer();
    }

    @PreDestroy
    public void shutdown() {
        log.info("[Disruptor] 종료 시작...");
        if (ingestDisruptor != null) {
            ingestDisruptor.shutdown();
            log.info("[Disruptor] Ingest Disruptor 종료 완료");
        }
    }

    private WaitStrategy resolveWaitStrategy() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                log.info("[Disruptor] prod 프로파일 → YieldingWaitStrategy (저지연)");
                return new YieldingWaitStrategy();
            }
        }
        log.info("[Disruptor] dev/local 프로파일 → SleepingWaitStrategy (저CPU)");
        return new SleepingWaitStrategy();
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
