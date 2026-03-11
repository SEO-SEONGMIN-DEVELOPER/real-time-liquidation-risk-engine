package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.MonteCarloReport;
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
        String userId = event.getUserId();
        CascadeRiskReport report = event.getReport();
        if (report != null && report.getSymbol() != null && userId != null && !userId.isBlank()) {
            broadcastCascadeRisk(userId, report, event.getCalcNanoTime());
        }

        MonteCarloReport mcReport = event.getMcReport();
        if (mcReport != null && mcReport.getSymbol() != null && userId != null && !userId.isBlank()) {
            broadcastMonteCarlo(userId, mcReport);
        }
    }

    private void broadcastCascadeRisk(String userId, CascadeRiskReport report, long calcNanoTime) {
        String destination = "/topic/risk/" + userId + "/" + report.getSymbol().toUpperCase();
        messagingTemplate.convertAndSend(destination, report);

        log.debug("[Broadcast] Cascade → {}, userId={}, risk={}, reachProb={}%, calc={}μs",
                destination, userId,
                report.getRiskLevel(),
                String.format("%.1f", report.getCascadeReachProbability()),
                calcNanoTime / 1_000);
    }

    private void broadcastMonteCarlo(String userId, MonteCarloReport mcReport) {
        String destination = "/topic/mc/" + userId + "/" + mcReport.getSymbol().toUpperCase();
        messagingTemplate.convertAndSend(destination, mcReport);

        log.debug("[Broadcast] MC → {}, userId={}, risk={}, σ={:.4f}, calc={}μs",
                destination, userId,
                mcReport.getRiskLevel(),
                mcReport.getSigma(), mcReport.getCalcDurationMicros());
    }
}
