package com.liquidation.riskengine.infra.disruptor.monitor;

import com.lmax.disruptor.RingBuffer;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DisruptorMetricsCollector {

    private final RingBuffer<MarketDataEvent> ingestRingBuffer;
    private final RingBuffer<RiskResultEvent> outputRingBuffer;
    private final MeterRegistry meterRegistry;

    public DisruptorMetricsCollector(
            RingBuffer<MarketDataEvent> marketDataRingBuffer,
            RingBuffer<RiskResultEvent> riskResultRingBuffer,
            MeterRegistry meterRegistry) {
        this.ingestRingBuffer = marketDataRingBuffer;
        this.outputRingBuffer = riskResultRingBuffer;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        Gauge.builder("disruptor.ringbuffer.utilization", ingestRingBuffer,
                        rb -> 1.0 - ((double) rb.remainingCapacity() / rb.getBufferSize()))
                .tag("pipeline", "ingest")
                .description("Ingest RingBuffer utilization (0.0~1.0)")
                .register(meterRegistry);

        Gauge.builder("disruptor.ringbuffer.utilization", outputRingBuffer,
                        rb -> 1.0 - ((double) rb.remainingCapacity() / rb.getBufferSize()))
                .tag("pipeline", "output")
                .description("Output RingBuffer utilization (0.0~1.0)")
                .register(meterRegistry);

        Gauge.builder("disruptor.ringbuffer.remaining", ingestRingBuffer,
                        rb -> (double) rb.remainingCapacity())
                .tag("pipeline", "ingest")
                .description("Ingest RingBuffer remaining capacity")
                .register(meterRegistry);

        Gauge.builder("disruptor.ringbuffer.remaining", outputRingBuffer,
                        rb -> (double) rb.remainingCapacity())
                .tag("pipeline", "output")
                .description("Output RingBuffer remaining capacity")
                .register(meterRegistry);

        log.info("[Metrics] Disruptor RingBuffer 모니터링 등록 완료");
    }

    @Scheduled(fixedRate = 30_000)
    public void logMetricsSummary() {
        double ingestUtil = 1.0 - ((double) ingestRingBuffer.remainingCapacity() / ingestRingBuffer.getBufferSize());
        double outputUtil = 1.0 - ((double) outputRingBuffer.remainingCapacity() / outputRingBuffer.getBufferSize());

        Timer riskCalcTimer = meterRegistry.find("disruptor.risk.calc_duration").timer();
        Timer e2eTimer = meterRegistry.find("disruptor.event.e2e_latency").timer();

        log.info("[Metrics] Ingest RB: {}% ({}/{}) | Output RB: {}% ({}/{})",
                String.format("%.1f", ingestUtil * 100),
                ingestRingBuffer.getBufferSize() - ingestRingBuffer.remainingCapacity(),
                ingestRingBuffer.getBufferSize(),
                String.format("%.1f", outputUtil * 100),
                outputRingBuffer.getBufferSize() - outputRingBuffer.remainingCapacity(),
                outputRingBuffer.getBufferSize());

        if (riskCalcTimer != null) {
            log.info("[Metrics] RiskCalc avg={}μs cnt={} | e2e avg={}μs",
                    String.format("%.0f", riskCalcTimer.mean(TimeUnit.MICROSECONDS)),
                    (long) riskCalcTimer.count(),
                    e2eTimer != null ? String.format("%.0f", e2eTimer.mean(TimeUnit.MICROSECONDS)) : "N/A");
        }
    }
}
