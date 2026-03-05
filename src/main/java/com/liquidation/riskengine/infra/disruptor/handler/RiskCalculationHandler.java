package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.service.CascadeRiskCalculator;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
import com.liquidation.riskengine.domain.model.UserPosition;
import com.liquidation.riskengine.domain.service.RiskStateManager;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import com.liquidation.riskengine.infra.disruptor.event.RiskResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskCalculationHandler implements EventHandler<MarketDataEvent> {

    private final CascadeRiskCalculator cascadeRiskCalculator;
    private final RiskStateManager riskStateManager;
    private final MarkPriceCache markPriceCache;
    private final RingBuffer<RiskResultEvent> riskResultRingBuffer;

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) {
        if (event.getType() == null || !event.getType().triggersRiskCalc()) {
            return;
        }

        String symbol = event.getSymbol();
        if (symbol == null) return;

        UserPosition position = riskStateManager.getPosition(symbol);
        if (position == null) return;

        BigDecimal currentPrice = markPriceCache.get(symbol);
        if (currentPrice == null) return;

        try {
            long startNano = System.nanoTime();

            CascadeRiskReport report = cascadeRiskCalculator.fullAnalysis(
                    currentPrice,
                    position.getLiquidationPrice(),
                    position.getPositionSide(),
                    symbol,
                    riskStateManager);

            long elapsed = System.nanoTime() - startNano;

            riskResultRingBuffer.publishEvent((resultEvent, seq) -> {
                resultEvent.clear();
                resultEvent.setReport(report);
                resultEvent.setCalcNanoTime(elapsed);
            });

            log.debug("[RiskCalc] {} | risk={} | reachProb={}% | 계산소요={}μs",
                    symbol, report.getRiskLevel(),
                    String.format("%.1f", report.getCascadeReachProbability()),
                    elapsed / 1_000);
        } catch (Exception e) {
            log.error("[RiskCalc] 위험 계산 실패: symbol={}", symbol, e);
        }
    }
}
