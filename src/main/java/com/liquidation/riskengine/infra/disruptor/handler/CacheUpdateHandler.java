package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.service.LeverageDistributionService;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
import com.liquidation.riskengine.domain.service.RiskStateManager;
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

    private final MarkPriceCache markPriceCache;
    private final LeverageDistributionService leverageDistributionService;
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

        markPriceCache.update(mp.getSymbol(), mp.getMarkPrice());
        log.debug("[Cache] MARK_PRICE 갱신: symbol={}, price={}", mp.getSymbol(), mp.getMarkPrice());
    }

    private void handleForceOrder(MarketDataEvent event) {
        LiquidationEvent liq = event.getLiquidationEvent();
        if (liq == null) return;

        riskStateManager.addLiquidation(liq);

        BigDecimal markPrice = markPriceCache.get(liq.getSymbol());
        if (markPrice != null) {
            leverageDistributionService.recordLiquidation(liq, markPrice);
            log.debug("[Cache] FORCE_ORDER 처리: symbol={}, side={}, 레버리지 분포 업데이트",
                    liq.getSymbol(), liq.getSide());
        } else {
            log.debug("[Cache] FORCE_ORDER markPrice 미존재: symbol={}, 레버리지 분포 업데이트 스킵",
                    liq.getSymbol());
        }
    }

    private void handleOrderBook(MarketDataEvent event) {
        if (event.getOrderBook() == null) return;
        riskStateManager.updateOrderBook(event.getOrderBook());
        log.debug("[Cache] ORDER_BOOK 갱신: symbol={}", event.getOrderBook().getSymbol());
    }

    private void handleOiUpdate(MarketDataEvent event) {
        if (event.getOpenInterest() == null) return;
        riskStateManager.updateOpenInterest(event.getOpenInterest());
        log.debug("[Cache] OI_UPDATE 갱신: symbol={}, oi={}",
                event.getOpenInterest().getSymbol(), event.getOpenInterest().getOpenInterest());
    }
}
