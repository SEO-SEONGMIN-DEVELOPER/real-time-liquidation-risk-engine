package com.liquidation.riskengine.infra.disruptor.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEventFactory;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEventFactory;
import com.liquidation.riskengine.infra.disruptor.handler.CacheUpdateHandler;
import com.liquidation.riskengine.infra.disruptor.handler.JournalEventHandler;
import com.liquidation.riskengine.infra.disruptor.handler.ParseEventHandler;
import com.liquidation.riskengine.infra.disruptor.handler.RiskCalculationHandler;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DisruptorConfig {

    private static final int INGEST_BUFFER_SIZE = 1024 * 64;
    private static final int OUTPUT_BUFFER_SIZE = 1024 * 16;

    private final ParseEventHandler parseEventHandler;
    private final JournalEventHandler journalEventHandler;
    private final CacheUpdateHandler cacheUpdateHandler;
    private final RiskCalculationHandler riskCalculationHandler;

    private Disruptor<MarketDataEvent> ingestDisruptor;
    private Disruptor<RiskResultEvent> outputDisruptor;

    @Bean
    public Disruptor<MarketDataEvent> marketDataDisruptor() {
        ingestDisruptor = new Disruptor<>(
                new MarketDataEventFactory(),
                INGEST_BUFFER_SIZE,
                namedThreadFactory("disruptor-ingest"),
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        ingestDisruptor
                .handleEventsWith(parseEventHandler)
                .then(journalEventHandler, cacheUpdateHandler);

        ingestDisruptor
                .after(cacheUpdateHandler)
                .then(riskCalculationHandler);

        ingestDisruptor.start();

        log.info("[Disruptor] Ingest 파이프라인 기동 완료: Parse → (Journal || Cache → RiskCalc) | size={}, producer=SINGLE",
                INGEST_BUFFER_SIZE);

        return ingestDisruptor;
    }

    @Bean
    public RingBuffer<MarketDataEvent> marketDataRingBuffer(Disruptor<MarketDataEvent> marketDataDisruptor) {
        return marketDataDisruptor.getRingBuffer();
    }

    @Bean
    public Disruptor<RiskResultEvent> riskResultDisruptor() {
        outputDisruptor = new Disruptor<>(
                new RiskResultEventFactory(),
                OUTPUT_BUFFER_SIZE,
                namedThreadFactory("disruptor-output"),
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        log.info("[Disruptor] Output RingBuffer 생성 완료 (size={}, wait=YieldingWaitStrategy, producer=SINGLE)",
                OUTPUT_BUFFER_SIZE);

        return outputDisruptor;
    }

    @Bean
    public RingBuffer<RiskResultEvent> riskResultRingBuffer(Disruptor<RiskResultEvent> riskResultDisruptor) {
        return riskResultDisruptor.getRingBuffer();
    }

    @PreDestroy
    public void shutdown() {
        log.info("[Disruptor] 종료 시작...");
        if (ingestDisruptor != null) {
            ingestDisruptor.shutdown();
            log.info("[Disruptor] Ingest Disruptor 종료 완료");
        }
        if (outputDisruptor != null) {
            outputDisruptor.shutdown();
            log.info("[Disruptor] Output Disruptor 종료 완료");
        }
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
