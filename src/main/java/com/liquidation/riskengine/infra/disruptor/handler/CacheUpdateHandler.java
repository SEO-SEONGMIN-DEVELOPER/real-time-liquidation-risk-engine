package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.domain.service.liquidation.LiquidationClusterMap;
import com.liquidation.riskengine.domain.service.state.PriceHistoryBuffer;
import com.liquidation.riskengine.domain.service.state.RiskStateManager;
import com.liquidation.riskengine.infra.binance.dto.MarkPriceEvent;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheUpdateHandler implements EventHandler<MarketDataEvent> {

    private final PriceHistoryBuffer priceHistoryBuffer;
    private final LiquidationClusterMap liquidationClusterMap;
    private final RiskStateManager riskStateManager;

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) {
        if (event.getType() == null) return;

        switch (event.getType()) {
            case MARK_PRICE -> handleMarkPrice(event);
            case FORCE_ORDER -> handleForceOrder(event);
            case ORDER_BOOK -> handleOrderBook(event);
            case OI_UPDATE -> handleOiUpdate(event);
            default -> { }
        }
    }

    private void handleMarkPrice(MarketDataEvent event) {
        MarkPriceEvent mp = event.getMarkPrice();
        if (mp == null) return;

        riskStateManager.updateMarkPrice(mp.getSymbol(), mp.getMarkPrice());
        priceHistoryBuffer.record(mp.getSymbol(), mp.getMarkPrice(), mp.getEventTime());
        log.debug("[Cache] MARK_PRICE 갱신: symbol={}, price={}", mp.getSymbol(), mp.getMarkPrice());
    }

    private void handleForceOrder(MarketDataEvent event) {
        LiquidationEvent liq = event.getLiquidationEvent();
        if (liq == null) return;

        riskStateManager.addLiquidation(liq);
        liquidationClusterMap.recordLiquidation(liq);
        log.debug("[Cache] FORCE_ORDER 처리: symbol={}, side={}, 청산맵 차감",
                liq.getSymbol(), liq.getSide());
    }

    private void handleOrderBook(MarketDataEvent event) {
        if (event.getOrderBook() == null) return;
        riskStateManager.updateOrderBook(event.getOrderBook());
        log.debug("[Cache] ORDER_BOOK 갱신: symbol={}", event.getOrderBook().getSymbol());
    }

    private void handleOiUpdate(MarketDataEvent event) {
        if (event.getOpenInterest() == null) return;
        OpenInterestSnapshot oi = event.getOpenInterest();
        riskStateManager.updateOpenInterest(oi);

        if (oi.getChange() != null && oi.getChange().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal markPrice = riskStateManager.getLatestMarkPrice(oi.getSymbol());
            if (markPrice != null) {
                liquidationClusterMap.recordOiIncrease(oi.getSymbol(), markPrice, oi.getChange());
            }
        }

        log.debug("[Cache] OI_UPDATE 갱신: symbol={}, oi={}, change={}",
                oi.getSymbol(), oi.getOpenInterest(), oi.getChange());
    }
}
