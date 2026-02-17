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
    private static final Duration RETENTION_PERIOD = Duration.ofHours(24);

    public void saveLiquidationEvent(LiquidationEvent event) {
        String key = LIQ_EVENTS_KEY + event.getSymbol().toUpperCase();
        redisTemplate.opsForZSet().add(key, event, event.getTimestamp());
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
        String key = LIQ_EVENTS_KEY + symbol.toUpperCase();
        return redisTemplate.opsForZSet().zCard(key);
    }

    public void saveOpenInterestSnapshot(OpenInterestSnapshot snapshot) {
        String key = OI_SNAPSHOTS_KEY + snapshot.getSymbol().toUpperCase();
        redisTemplate.opsForZSet().add(key, snapshot, snapshot.getTimestamp());

        String latestKey = OI_LATEST_KEY + snapshot.getSymbol().toUpperCase();
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
        String key = OB_SNAPSHOTS_KEY + snapshot.getSymbol().toUpperCase();
        redisTemplate.opsForZSet().add(key, snapshot, snapshot.getTimestamp());

        String latestKey = OB_LATEST_KEY + snapshot.getSymbol().toUpperCase();
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

        evictByPattern(LIQ_EVENTS_KEY, cutoff);
        evictByPattern(OI_SNAPSHOTS_KEY, cutoff);
        evictByPattern(OB_SNAPSHOTS_KEY, cutoff);

        log.debug("[Redis] 만료 데이터 정리 완료 (cutoff={})", cutoff);
    }

    private void evictByPattern(String prefix, long cutoff) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys == null) return;
        for (String key : keys) {
            Long removed = redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
            if (removed != null && removed > 0) {
                log.debug("[Redis] {} 에서 {} 건 만료 데이터 삭제", key, removed);
            }
        }
    }
}
