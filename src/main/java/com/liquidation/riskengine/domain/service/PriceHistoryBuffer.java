package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.PriceTick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PriceHistoryBuffer {

    static final int DEFAULT_CAPACITY = 86_400;

    private final Map<String, CircularBuffer> buffers = new ConcurrentHashMap<>();

    public void record(String symbol, BigDecimal price, long timestampMs) {
        if (symbol == null || price == null) return;

        String key = symbol.toUpperCase();
        CircularBuffer buffer = buffers.computeIfAbsent(key, k -> new CircularBuffer(DEFAULT_CAPACITY));
        buffer.add(new PriceTick(timestampMs, price));
    }

    public List<PriceTick> getRecentPrices(String symbol, Duration duration) {
        if (symbol == null || duration == null) return List.of();

        CircularBuffer buffer = buffers.get(symbol.toUpperCase());
        if (buffer == null) return List.of();

        long cutoffMs = System.currentTimeMillis() - duration.toMillis();
        return buffer.query(cutoffMs);
    }

    public int size(String symbol) {
        if (symbol == null) return 0;
        CircularBuffer buffer = buffers.get(symbol.toUpperCase());
        return buffer == null ? 0 : buffer.size();
    }

    static final class CircularBuffer {

        private final PriceTick[] elements;
        private final int mask;
        private volatile long head;
        private volatile long tail;

        CircularBuffer(int requestedCapacity) {
            int capacity = nextPowerOfTwo(requestedCapacity);
            this.elements = new PriceTick[capacity];
            this.mask = capacity - 1;
        }

        void add(PriceTick tick) {
            long t = tail;
            long h = head;
            if (t - h == elements.length) {
                head = h + 1;
            }
            elements[(int) (t & mask)] = tick;
            tail = t + 1;
        }

        List<PriceTick> query(long fromTimestampMs) {
            long h = head;
            long t = tail;
            int count = (int) (t - h);
            if (count == 0) return List.of();

            int startIdx = binarySearchStart(h, t, fromTimestampMs);
            int resultSize = (int) (t - startIdx);
            if (resultSize <= 0) return List.of();

            List<PriceTick> result = new ArrayList<>(resultSize);
            for (long i = startIdx; i < t; i++) {
                PriceTick tick = elements[(int) (i & mask)];
                if (tick != null) {
                    result.add(tick);
                }
            }
            return result;
        }

        int size() {
            long h = head;
            long t = tail;
            return (int) (t - h);
        }

        private int binarySearchStart(long head, long tail, long fromTimestampMs) {
            long lo = head;
            long hi = tail;
            while (lo < hi) {
                long mid = lo + ((hi - lo) >>> 1);
                PriceTick tick = elements[(int) (mid & mask)];
                if (tick == null || tick.timestamp() < fromTimestampMs) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return (int) lo;
        }

        private static int nextPowerOfTwo(int value) {
            if (value <= 0) return 1;
            return Integer.highestOneBit(value - 1) << 1;
        }
    }
}
