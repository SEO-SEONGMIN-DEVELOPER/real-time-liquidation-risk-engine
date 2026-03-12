package com.liquidation.riskengine.infra.redis.service;

import com.liquidation.riskengine.domain.model.LiquidationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTimeSeriesService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LIQ_EVENTS_KEY = "liq:events:";
    private static final String LIQ_SYMBOLS_SET_KEY = "symbols:liq";
    private static final Duration RETENTION_PERIOD = Duration.ofHours(24);

    public void saveLiquidationEvent(LiquidationEvent event) {
        String symbol = event.getSymbol().toUpperCase(Locale.ROOT);
        String key = LIQ_EVENTS_KEY + symbol;
        redisTemplate.opsForZSet().add(key, event, event.getTimestamp());
        redisTemplate.opsForSet().add(LIQ_SYMBOLS_SET_KEY, symbol);
        log.debug("[Redis] 청산 이벤트 저장: symbol={}, side={}, price={}, qty={}, ts={}",
                event.getSymbol(), event.getSide(), event.getPrice(),
                event.getQuantity(), event.getTimestamp());
    }

    public List<LiquidationEvent> getLiquidationEvents(String symbol, long fromTimestamp, long toTimestamp) {
        String key = LIQ_EVENTS_KEY + symbol.toUpperCase();
        Set<ZSetOperations.TypedTuple<Object>> results =
                redisTemplate.opsForZSet().rangeByScoreWithScores(key, fromTimestamp, toTimestamp);
        if (results == null || results.isEmpty()) return Collections.emptyList();
        return results.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(LiquidationEvent.class::isInstance)
                .map(LiquidationEvent.class::cast)
                .toList();
    }

    public List<LiquidationEvent> getRecentLiquidationEvents(String symbol, Duration window) {
        long now = Instant.now().toEpochMilli();
        long from = now - window.toMillis();
        return getLiquidationEvents(symbol, from, now);
    }

    public Long getLiquidationEventCount(String symbol) {
        String key = LIQ_EVENTS_KEY + symbol.toUpperCase(Locale.ROOT);
        return redisTemplate.opsForZSet().zCard(key);
    }

    @Scheduled(fixedRate = 300_000)
    public void evictExpiredData() {
        long cutoff = Instant.now().minus(RETENTION_PERIOD).toEpochMilli();

        evictBySymbols(LIQ_EVENTS_KEY, LIQ_SYMBOLS_SET_KEY, cutoff);

        log.debug("[Redis] 만료 데이터 정리 완료 (cutoff={})", cutoff);
    }

    private void evictBySymbols(String prefix, String symbolsSetKey, long cutoff) {
        Set<Object> symbols = redisTemplate.opsForSet().members(symbolsSetKey);
        if (symbols == null || symbols.isEmpty()) return;

        for (Object symbolObj : symbols) {
            if (symbolObj == null) continue;
            String symbol = String.valueOf(symbolObj).toUpperCase(Locale.ROOT);
            String key = prefix + symbol;
            Long removed = redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
            if (removed != null && removed > 0) {
                log.debug("[Redis] {} 에서 {} 건 만료 데이터 삭제", key, removed);
            }

            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size != null && size == 0) {
                redisTemplate.delete(key);
                redisTemplate.opsForSet().remove(symbolsSetKey, symbol);
            }
        }
    }
}
