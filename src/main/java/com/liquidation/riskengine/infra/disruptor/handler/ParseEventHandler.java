package com.liquidation.riskengine.infra.disruptor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventHandler;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.infra.binance.dto.ForceOrderEvent;
import com.liquidation.riskengine.infra.binance.dto.MarkPriceEvent;
import com.liquidation.riskengine.infra.binance.dto.OrderBookDepthEvent;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseEventHandler implements EventHandler<MarketDataEvent> {

    private final ObjectMapper objectMapper;

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) {
        if (event.getType() == null) return;

        String rawJson = event.getRawJson();

        switch (event.getType()) {
            case MARK_PRICE -> parseMarkPrice(event, rawJson);
            case FORCE_ORDER -> parseForceOrder(event, rawJson);
            case ORDER_BOOK -> parseOrderBook(event, rawJson);
            case OI_UPDATE -> {} 
            default -> log.debug("[Parse] 미지원 이벤트 타입: {}", event.getType());
        }
    }

    private void parseMarkPrice(MarketDataEvent event, String rawJson) {
        try {
            MarkPriceEvent markPrice = objectMapper.readValue(rawJson, MarkPriceEvent.class);
            event.setMarkPrice(markPrice);
            log.debug("[Parse] MARK_PRICE symbol={}, price={}", markPrice.getSymbol(), markPrice.getMarkPrice());
        } catch (Exception e) {
            log.error("[Parse] MARK_PRICE 파싱 실패: {}", rawJson, e);
        }
    }

    private void parseForceOrder(MarketDataEvent event, String rawJson) {
        try {
            ForceOrderEvent forceOrder = objectMapper.readValue(rawJson, ForceOrderEvent.class);
            ForceOrderEvent.Order order = forceOrder.getOrder();

            BigDecimal price = order.getAveragePrice() != null && order.getAveragePrice().compareTo(BigDecimal.ZERO) > 0
                    ? order.getAveragePrice()
                    : order.getPrice();
            BigDecimal notional = price.multiply(order.getOriginalQuantity(), new MathContext(12));

            LiquidationEvent liqEvent = LiquidationEvent.builder()
                    .symbol(order.getSymbol())
                    .side(order.getSide())
                    .price(price)
                    .averagePrice(order.getAveragePrice())
                    .quantity(order.getOriginalQuantity())
                    .notionalValue(notional)
                    .orderStatus(order.getOrderStatus())
                    .timestamp(forceOrder.getEventTime() != null ? forceOrder.getEventTime() : Instant.now().toEpochMilli())
                    .build();

            event.setLiquidationEvent(liqEvent);
            log.info("[Parse] FORCE_ORDER symbol={}, side={}, price={}, qty={}, notional={}",
                    order.getSymbol(), order.getSide(), price, order.getOriginalQuantity(), notional);
        } catch (Exception e) {
            log.error("[Parse] FORCE_ORDER 파싱 실패: {}", rawJson, e);
        }
    }

    private void parseOrderBook(MarketDataEvent event, String rawJson) {
        try {
            OrderBookDepthEvent depthEvent = objectMapper.readValue(rawJson, OrderBookDepthEvent.class);

            List<OrderBookSnapshot.PriceLevel> bids = parsePriceLevels(depthEvent.getBids());
            List<OrderBookSnapshot.PriceLevel> asks = parsePriceLevels(depthEvent.getAsks());

            BigDecimal bestBid = bids.isEmpty() ? BigDecimal.ZERO : bids.get(0).getPrice();
            BigDecimal bestAsk = asks.isEmpty() ? BigDecimal.ZERO : asks.get(0).getPrice();
            BigDecimal spread = bestAsk.subtract(bestBid).setScale(2, RoundingMode.HALF_UP);

            BigDecimal bidTotal = bids.stream()
                    .map(OrderBookSnapshot.PriceLevel::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal askTotal = asks.stream()
                    .map(OrderBookSnapshot.PriceLevel::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            OrderBookSnapshot snapshot = OrderBookSnapshot.builder()
                    .symbol(depthEvent.getSymbol())
                    .bids(bids)
                    .asks(asks)
                    .bestBid(bestBid)
                    .bestAsk(bestAsk)
                    .spread(spread)
                    .bidTotalQuantity(bidTotal)
                    .askTotalQuantity(askTotal)
                    .timestamp(depthEvent.getEventTime() != null ? depthEvent.getEventTime() : Instant.now().toEpochMilli())
                    .build();

            event.setOrderBook(snapshot);
            log.debug("[Parse] ORDER_BOOK symbol={}, bestBid={}, bestAsk={}, spread={}, bids={}, asks={}",
                    depthEvent.getSymbol(), bestBid, bestAsk, spread, bids.size(), asks.size());
        } catch (Exception e) {
            log.error("[Parse] ORDER_BOOK 파싱 실패: {}", rawJson, e);
        }
    }

    private List<OrderBookSnapshot.PriceLevel> parsePriceLevels(List<List<String>> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        return raw.stream()
                .filter(entry -> entry.size() >= 2)
                .map(entry -> OrderBookSnapshot.PriceLevel.builder()
                        .price(new BigDecimal(entry.get(0)))
                        .quantity(new BigDecimal(entry.get(1)))
                        .build())
                .toList();
    }
}
