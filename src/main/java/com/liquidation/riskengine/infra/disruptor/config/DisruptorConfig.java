package com.liquidation.riskengine.infra.disruptor.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEventFactory;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEventFactory;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class DisruptorConfig {

    private static final int INGEST_BUFFER_SIZE = 1024 * 64;
    private static final int OUTPUT_BUFFER_SIZE = 1024 * 16;

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

        log.info("[Disruptor] Ingest RingBuffer 생성 완료 (size={}, wait=YieldingWaitStrategy, producer=SINGLE)",
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
