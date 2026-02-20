package com.liquidation.riskengine.infra.disruptor.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEventFactory;
import com.liquidation.riskengine.infra.disruptor.handler.RiskBroadcastHandler;
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
public class OutputDisruptorConfig {

    private static final int OUTPUT_BUFFER_SIZE = 1024 * 16;

    private final RiskBroadcastHandler riskBroadcastHandler;

    private Disruptor<RiskResultEvent> outputDisruptor;

    @Bean
    public Disruptor<RiskResultEvent> riskResultDisruptor() {
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory tf = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("disruptor-output-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

        outputDisruptor = new Disruptor<>(
                new RiskResultEventFactory(),
                OUTPUT_BUFFER_SIZE,
                tf,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        outputDisruptor.handleEventsWith(riskBroadcastHandler);
        outputDisruptor.start();

        log.info("[Disruptor] Output 파이프라인 기동 완료: RiskResult → STOMP Broadcast | size={}, producer=SINGLE",
                OUTPUT_BUFFER_SIZE);

        return outputDisruptor;
    }

    @Bean
    public RingBuffer<RiskResultEvent> riskResultRingBuffer(Disruptor<RiskResultEvent> riskResultDisruptor) {
        return riskResultDisruptor.getRingBuffer();
    }

    @PreDestroy
    public void shutdown() {
        if (outputDisruptor != null) {
            outputDisruptor.shutdown();
            log.info("[Disruptor] Output Disruptor 종료 완료");
        }
    }
}
