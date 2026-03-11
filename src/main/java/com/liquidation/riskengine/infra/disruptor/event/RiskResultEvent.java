package com.liquidation.riskengine.infra.disruptor.event;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.MonteCarloReport;

public class RiskResultEvent {

    private CascadeRiskReport report;
    private MonteCarloReport mcReport;
    private long calcNanoTime;

    public void clear() {
        report = null;
        mcReport = null;
        calcNanoTime = 0L;
    }

    public CascadeRiskReport getReport() {
        return report;
    }

    public void setReport(CascadeRiskReport report) {
        this.report = report;
    }

    public MonteCarloReport getMcReport() {
        return mcReport;
    }

    public void setMcReport(MonteCarloReport mcReport) {
        this.mcReport = mcReport;
    }

    public long getCalcNanoTime() {
        return calcNanoTime;
    }

    public void setCalcNanoTime(long calcNanoTime) {
        this.calcNanoTime = calcNanoTime;
    }

    @Override
    public String toString() {
        String sym = report != null ? report.getSymbol()
                : (mcReport != null ? mcReport.getSymbol() : "null");
        return "RiskResultEvent{symbol=" + sym + ", cascade=" + (report != null)
                + ", mc=" + (mcReport != null) + "}";
    }
}
