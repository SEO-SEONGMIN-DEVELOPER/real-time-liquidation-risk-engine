package com.liquidation.riskengine.infra.disruptor.event;

import com.lmax.disruptor.EventFactory;

public class RiskResultEventFactory implements EventFactory<RiskResultEvent> {

    @Override
    public RiskResultEvent newInstance() {
        return new RiskResultEvent();
    }
}
