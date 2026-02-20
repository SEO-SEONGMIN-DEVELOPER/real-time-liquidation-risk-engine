package com.liquidation.riskengine.infra.disruptor.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEventFactory;
import com.liquidation.riskengine.infra.disruptor.handler.DisruptorExceptionHandler;
import com.liquidation.riskengine.infra.disruptor.handler.RiskBroadcastHandler;
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
public class OutputDisruptorConfig {

    private static final int OUTPUT_BUFFER_SIZE = 1024 * 16;

    private final RiskBroadcastHandler riskBroadcastHandler;
    private final MeterRegistry meterRegistry;
    private final Environment environment;

    private Disruptor<RiskResultEvent> outputDisruptor;

    @Bean
    public Disruptor<RiskResultEvent> riskResultDisruptor() {
        WaitStrategy waitStrategy = resolveWaitStrategy();

        outputDisruptor = new Disruptor<>(
                new RiskResultEventFactory(),
                OUTPUT_BUFFER_SIZE,
                namedThreadFactory("disruptor-output"),
                ProducerType.SINGLE,
                waitStrategy
        );

        outputDisruptor.setDefaultExceptionHandler(
                new DisruptorExceptionHandler<>("output", meterRegistry));

        outputDisruptor.handleEventsWith(riskBroadcastHandler);
        outputDisruptor.start();

        log.info("[Disruptor] Output 파이프라인 기동: RiskResult → STOMP Broadcast | size={}, wait={}",
                OUTPUT_BUFFER_SIZE, waitStrategy.getClass().getSimpleName());

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

    private WaitStrategy resolveWaitStrategy() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                return new YieldingWaitStrategy();
            }
        }
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
