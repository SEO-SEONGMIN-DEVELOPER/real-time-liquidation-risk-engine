package com.liquidation.riskengine.infra.disruptor.event;

import com.lmax.disruptor.EventFactory;

public class MarketDataEventFactory implements EventFactory<MarketDataEvent> {

    @Override
    public MarketDataEvent newInstance() {
        return new MarketDataEvent();
    }
}
