package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.ExceptionHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisruptorExceptionHandler<T> implements ExceptionHandler<T> {

    private final String pipelineName;
    private final Counter exceptionCounter;

    public DisruptorExceptionHandler(String pipelineName, MeterRegistry meterRegistry) {
        this.pipelineName = pipelineName;
        this.exceptionCounter = Counter.builder("disruptor.exceptions")
                .tag("pipeline", pipelineName)
                .description("Disruptor pipeline exception count")
                .register(meterRegistry);
    }

    @Override
    public void handleEventException(Throwable ex, long sequence, T event) {
        exceptionCounter.increment();
        log.error("[Disruptor-{}] 이벤트 처리 예외 (seq={}, event={}). 드롭 후 계속 진행.",
                pipelineName, sequence, event, ex);
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        log.error("[Disruptor-{}] 핸들러 시작 예외", pipelineName, ex);
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        log.error("[Disruptor-{}] 핸들러 종료 예외", pipelineName, ex);
    }
}
