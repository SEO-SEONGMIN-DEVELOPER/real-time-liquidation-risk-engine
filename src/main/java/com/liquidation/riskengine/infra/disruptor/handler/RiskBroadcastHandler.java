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
        CascadeRiskReport report = event.getReport();
        if (report != null && report.getSymbol() != null) {
            broadcastCascadeRisk(report, event.getCalcNanoTime());
        }

        MonteCarloReport mcReport = event.getMcReport();
        if (mcReport != null && mcReport.getSymbol() != null) {
            broadcastMonteCarlo(mcReport);
        }
    }

    private void broadcastCascadeRisk(CascadeRiskReport report, long calcNanoTime) {
        String destination = "/topic/risk/" + report.getSymbol().toUpperCase();
        messagingTemplate.convertAndSend(destination, report);

        log.debug("[Broadcast] Cascade → {}, risk={}, reachProb={}%, calc={}μs",
                destination, report.getRiskLevel(),
                String.format("%.1f", report.getCascadeReachProbability()),
                calcNanoTime / 1_000);
    }

    private void broadcastMonteCarlo(MonteCarloReport mcReport) {
        String destination = "/topic/mc/" + mcReport.getSymbol().toUpperCase();
        messagingTemplate.convertAndSend(destination, mcReport);

        log.debug("[Broadcast] MC → {}, risk={}, σ={:.4f}, calc={}μs",
                destination, mcReport.getRiskLevel(),
                mcReport.getSigma(), mcReport.getCalcDurationMicros());
    }
}
