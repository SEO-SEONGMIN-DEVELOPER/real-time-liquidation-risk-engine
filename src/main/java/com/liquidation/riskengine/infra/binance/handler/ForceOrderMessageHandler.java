package com.liquidation.riskengine.infra.binance.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquidation.riskengine.infra.binance.dto.ForceOrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForceOrderMessageHandler implements BinanceMessageHandler {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String streamName) {
        return streamName != null && streamName.contains("@forceOrder");
    }

    @Override
    public void handle(String rawJson) {
        try {
            ForceOrderEvent event = objectMapper.readValue(rawJson, ForceOrderEvent.class);
            ForceOrderEvent.Order order = event.getOrder();

            log.info("[ForceOrder] symbol={}, side={}, price={}, avgPrice={}, qty={}, status={}",
                    order.getSymbol(),
                    order.getSide(),
                    order.getPrice(),
                    order.getAveragePrice(),
                    order.getOriginalQuantity(),
                    order.getOrderStatus());

        } catch (Exception e) {
            log.error("[ForceOrder] 메시지 파싱 실패: {}", rawJson, e);
        }
    }
}
