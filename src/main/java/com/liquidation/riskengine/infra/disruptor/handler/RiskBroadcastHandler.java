package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskBroadcastHandler implements EventHandler<RiskResultEvent> {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onEvent(RiskResultEvent event, long sequence, boolean endOfBatch) {
        CascadeRiskReport report = event.getReport();
        if (report == null || report.getSymbol() == null) return;

        String destination = "/topic/risk/" + report.getSymbol().toUpperCase();
        messagingTemplate.convertAndSend(destination, report);

        log.debug("[Broadcast] STOMP 전송: dest={}, risk={}, reachProb={}%, 계산소요={}μs",
                destination, report.getRiskLevel(),
                String.format("%.1f", report.getCascadeReachProbability()),
                event.getCalcNanoTime() / 1_000);
    }
}
