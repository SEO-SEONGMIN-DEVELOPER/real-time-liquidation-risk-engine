package com.liquidation.riskengine.infra.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JournalEventHandler implements EventHandler<MarketDataEvent> {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LIQ_EVENTS_KEY = "liq:events:";
    private static final String OB_SNAPSHOTS_KEY = "ob:snapshots:";
    private static final String OB_LATEST_KEY = "ob:latest:";

    private final List<LiquidationEvent> pendingLiquidations = new ArrayList<>();
    private final List<OrderBookSnapshot> pendingOrderBooks = new ArrayList<>();

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
            case ORDER_BOOK -> {
                if (event.getOrderBook() != null) {
                    pendingOrderBooks.add(event.getOrderBook());
                }
            }
            default -> { }
        }
    }

    @SuppressWarnings("unchecked")
    private void flush() {
        int totalOps = pendingLiquidations.size() + pendingOrderBooks.size();
        if (totalOps == 0) return;

        try {
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    var ops = (RedisOperations<String, Object>) operations;

                    for (LiquidationEvent liq : pendingLiquidations) {
                        String key = LIQ_EVENTS_KEY + liq.getSymbol().toUpperCase();
                        ops.opsForZSet().add(key, liq, liq.getTimestamp());
                    }

                    for (OrderBookSnapshot ob : pendingOrderBooks) {
                        String key = OB_SNAPSHOTS_KEY + ob.getSymbol().toUpperCase();
                        ops.opsForZSet().add(key, ob, ob.getTimestamp());

                        String latestKey = OB_LATEST_KEY + ob.getSymbol().toUpperCase();
                        ops.opsForValue().set(latestKey, ob);
                    }

                    return null;
                }
            });

            log.debug("[Journal] 배치 flush 완료: liquidations={}, orderBooks={}, totalOps={}",
                    pendingLiquidations.size(), pendingOrderBooks.size(), totalOps);
        } catch (Exception e) {
            log.error("[Journal] Redis 파이프라인 flush 실패: liquidations={}, orderBooks={}",
                    pendingLiquidations.size(), pendingOrderBooks.size(), e);
        } finally {
            pendingLiquidations.clear();
            pendingOrderBooks.clear();
        }
    }
}
