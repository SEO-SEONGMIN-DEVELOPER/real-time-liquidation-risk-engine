package com.liquidation.riskengine.infra.binance.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.infra.binance.dto.OrderBookDepthEvent;
import com.liquidation.riskengine.infra.redis.service.RedisTimeSeriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookMessageHandler implements BinanceMessageHandler {

    private final ObjectMapper objectMapper;
    private final RedisTimeSeriesService redisTimeSeriesService;

    @Override
    public boolean supports(String streamName) {
        return streamName != null && streamName.contains("@depth");
    }

    @Override
    public void handle(String rawJson) {
        try {
            OrderBookDepthEvent event = objectMapper.readValue(rawJson, OrderBookDepthEvent.class);

            List<OrderBookSnapshot.PriceLevel> bids = parsePriceLevels(event.getBids());
            List<OrderBookSnapshot.PriceLevel> asks = parsePriceLevels(event.getAsks());

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
                    .symbol(event.getSymbol())
                    .bids(bids)
                    .asks(asks)
                    .bestBid(bestBid)
                    .bestAsk(bestAsk)
                    .spread(spread)
                    .bidTotalQuantity(bidTotal)
                    .askTotalQuantity(askTotal)
                    .timestamp(event.getEventTime() != null ? event.getEventTime() : Instant.now().toEpochMilli())
                    .build();

            redisTimeSeriesService.saveOrderBookSnapshot(snapshot);

            log.debug("[OrderBook] symbol={}, bestBid={}, bestAsk={}, spread={}, bids={}, asks={}",
                    event.getSymbol(), bestBid, bestAsk, spread, bids.size(), asks.size());

        } catch (Exception e) {
            log.error("[OrderBook] 메시지 처리 실패: {}", rawJson, e);
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
