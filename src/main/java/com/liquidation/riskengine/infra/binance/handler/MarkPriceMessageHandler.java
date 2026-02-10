package com.liquidation.riskengine.infra.binance.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquidation.riskengine.infra.binance.dto.MarkPriceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarkPriceMessageHandler implements BinanceMessageHandler {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String streamName) {
        return streamName != null && streamName.contains("@markPrice");
    }

    @Override
    public void handle(String rawJson) {
        try {
            MarkPriceEvent event = objectMapper.readValue(rawJson, MarkPriceEvent.class);
            log.info("[MarkPrice] symbol={}, markPrice={}, indexPrice={}, fundingRate={}",
                    event.getSymbol(),
                    event.getMarkPrice(),
                    event.getIndexPrice(),
                    event.getFundingRate());

        } catch (Exception e) {
            log.error("[MarkPrice] 메시지 파싱 실패: {}", rawJson, e);
        }
    }
}
