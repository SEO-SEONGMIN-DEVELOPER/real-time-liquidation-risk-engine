package com.liquidation.riskengine.infra.redis.service;

import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
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
    private static final String OI_SNAPSHOTS_KEY = "oi:snapshots:";
    private static final String OI_LATEST_KEY = "oi:latest:";
    private static final String OB_SNAPSHOTS_KEY = "ob:snapshots:";
    private static final String OB_LATEST_KEY = "ob:latest:";
    private static final String LIQ_SYMBOLS_SET_KEY = "symbols:liq";
    private static final String OI_SYMBOLS_SET_KEY = "symbols:oi";
    private static final String OB_SYMBOLS_SET_KEY = "symbols:ob";
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

    public void saveOpenInterestSnapshot(OpenInterestSnapshot snapshot) {
        String symbol = snapshot.getSymbol().toUpperCase(Locale.ROOT);
        String key = OI_SNAPSHOTS_KEY + symbol;
        redisTemplate.opsForZSet().add(key, snapshot, snapshot.getTimestamp());
        redisTemplate.opsForSet().add(OI_SYMBOLS_SET_KEY, symbol);

        String latestKey = OI_LATEST_KEY + symbol;
        redisTemplate.opsForValue().set(latestKey, snapshot.getOpenInterest());

        log.debug("[Redis] OI 스냅샷 저장: symbol={}, oi={}, change={}%, ts={}",
                snapshot.getSymbol(), snapshot.getOpenInterest(),
                snapshot.getChangePercent(), snapshot.getTimestamp());
    }

    public List<OpenInterestSnapshot> getOpenInterestSnapshots(String symbol, long fromTimestamp, long toTimestamp) {
        String key = OI_SNAPSHOTS_KEY + symbol.toUpperCase();
        Set<ZSetOperations.TypedTuple<Object>> results =
                redisTemplate.opsForZSet().rangeByScoreWithScores(key, fromTimestamp, toTimestamp);
        if (results == null || results.isEmpty()) return Collections.emptyList();
        return results.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(OpenInterestSnapshot.class::isInstance)
                .map(OpenInterestSnapshot.class::cast)
                .toList();
    }

    public List<OpenInterestSnapshot> getRecentOpenInterestSnapshots(String symbol, Duration window) {
        long now = Instant.now().toEpochMilli();
        long from = now - window.toMillis();
        return getOpenInterestSnapshots(symbol, from, now);
    }

    public Object getLatestOpenInterest(String symbol) {
        String latestKey = OI_LATEST_KEY + symbol.toUpperCase();
        return redisTemplate.opsForValue().get(latestKey);
    }

    public void saveOrderBookSnapshot(OrderBookSnapshot snapshot) {
        String symbol = snapshot.getSymbol().toUpperCase(Locale.ROOT);
        String key = OB_SNAPSHOTS_KEY + symbol;
        redisTemplate.opsForZSet().add(key, snapshot, snapshot.getTimestamp());
        redisTemplate.opsForSet().add(OB_SYMBOLS_SET_KEY, symbol);

        String latestKey = OB_LATEST_KEY + symbol;
        redisTemplate.opsForValue().set(latestKey, snapshot);

        log.debug("[Redis] 호가 스냅샷 저장: symbol={}, bestBid={}, bestAsk={}, spread={}, ts={}",
                snapshot.getSymbol(), snapshot.getBestBid(), snapshot.getBestAsk(),
                snapshot.getSpread(), snapshot.getTimestamp());
    }

    public List<OrderBookSnapshot> getOrderBookSnapshots(String symbol, long fromTimestamp, long toTimestamp) {
        String key = OB_SNAPSHOTS_KEY + symbol.toUpperCase();
        Set<ZSetOperations.TypedTuple<Object>> results =
                redisTemplate.opsForZSet().rangeByScoreWithScores(key, fromTimestamp, toTimestamp);
        if (results == null || results.isEmpty()) return Collections.emptyList();
        return results.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(OrderBookSnapshot.class::isInstance)
                .map(OrderBookSnapshot.class::cast)
                .toList();
    }

    public List<OrderBookSnapshot> getRecentOrderBookSnapshots(String symbol, Duration window) {
        long now = Instant.now().toEpochMilli();
        long from = now - window.toMillis();
        return getOrderBookSnapshots(symbol, from, now);
    }

    public OrderBookSnapshot getLatestOrderBook(String symbol) {
        String latestKey = OB_LATEST_KEY + symbol.toUpperCase();
        Object value = redisTemplate.opsForValue().get(latestKey);
        if (value instanceof OrderBookSnapshot snapshot) {
            return snapshot;
        }
        return null;
    }

    @Scheduled(fixedRate = 300_000)
    public void evictExpiredData() {
        long cutoff = Instant.now().minus(RETENTION_PERIOD).toEpochMilli();

        evictBySymbols(LIQ_EVENTS_KEY, LIQ_SYMBOLS_SET_KEY, cutoff);
        evictBySymbols(OI_SNAPSHOTS_KEY, OI_SYMBOLS_SET_KEY, cutoff);
        evictBySymbols(OB_SNAPSHOTS_KEY, OB_SYMBOLS_SET_KEY, cutoff);

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
