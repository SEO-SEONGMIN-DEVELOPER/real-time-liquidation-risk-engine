package com.liquidation.riskengine.infra.binance.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.infra.binance.dto.ForceOrderEvent;
import com.liquidation.riskengine.infra.redis.service.RedisTimeSeriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForceOrderMessageHandler implements BinanceMessageHandler {

    private final ObjectMapper objectMapper;
    private final RedisTimeSeriesService redisTimeSeriesService;

    @Override
    public boolean supports(String streamName) {
        return streamName != null && streamName.contains("@forceOrder");
    }

    @Override
    public void handle(String rawJson) {
        try {
            ForceOrderEvent event = objectMapper.readValue(rawJson, ForceOrderEvent.class);
            ForceOrderEvent.Order order = event.getOrder();

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
                    .timestamp(event.getEventTime() != null ? event.getEventTime() : Instant.now().toEpochMilli())
                    .build();

            redisTimeSeriesService.saveLiquidationEvent(liqEvent);

            log.info("[ForceOrder] symbol={}, side={}, price={}, qty={}, notional={}, status={} → Redis 저장 완료",
                    order.getSymbol(), order.getSide(), price,
                    order.getOriginalQuantity(), notional, order.getOrderStatus());

        } catch (Exception e) {
            log.error("[ForceOrder] 메시지 처리 실패: {}", rawJson, e);
        }
    }
}
