package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class JournalEventHandler implements EventHandler<MarketDataEvent> {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LIQ_EVENTS_KEY = "liq:events:";
    private static final String LIQ_SYMBOLS_SET_KEY = "symbols:liq";

    private final List<LiquidationEvent> pendingLiquidations = new ArrayList<>();

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) {
        bufferEvent(event);

        if (endOfBatch) {
            flush();
        }
    }

    private void bufferEvent(MarketDataEvent event) {
        if (event.getType() == null) return;

        switch (event.getType()) {
            case FORCE_ORDER -> {
                if (event.getLiquidationEvent() != null) {
                    pendingLiquidations.add(event.getLiquidationEvent());
                }
            }
            default -> { }
        }
    }

    @SuppressWarnings("unchecked")
    private void flush() {
        int totalOps = pendingLiquidations.size();
        if (totalOps == 0) return;

        try {
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    var ops = (RedisOperations<String, Object>) operations;

                    for (LiquidationEvent liq : pendingLiquidations) {
                        String symbol = liq.getSymbol().toUpperCase(Locale.ROOT);
                        String key = LIQ_EVENTS_KEY + symbol;
                        ops.opsForZSet().add(key, liq, liq.getTimestamp());
                        ops.opsForSet().add(LIQ_SYMBOLS_SET_KEY, symbol);
                    }

                    return null;
                }
            });

            log.debug("[Journal] 배치 flush 완료: liquidations={}, totalOps={}",
                    pendingLiquidations.size(), totalOps);
        } catch (Exception e) {
            log.error("[Journal] Redis 파이프라인 flush 실패: liquidations={}",
                    pendingLiquidations.size(), e);
        } finally {
            pendingLiquidations.clear();
        }
    }
}
