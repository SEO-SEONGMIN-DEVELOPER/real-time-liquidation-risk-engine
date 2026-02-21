package com.liquidation.riskengine.domain.service.montecarlo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.SplittableRandom;

@Slf4j
@Component
public class PricePathGenerator {

    static final double MINUTES_PER_YEAR = 365.25 * 24 * 60;

    public double[][] generate(SimulationRequest request) {
        validateRequest(request);

        double s0 = request.getStartPrice();
        double sigma = request.getSigma();
        double mu = request.getMu();
        int pathCount = request.getPathCount();
        int stepMinutes = request.getTimeStepMinutes();
        int totalSteps = request.getHorizonMinutes() / stepMinutes;
        boolean fatTail = request.isUseFatTail();
        double nu = request.getDegreesOfFreedom();
        double[] sigmaSchedule = request.getSigmaSchedule();

        int evenPathCount = pathCount + (pathCount % 2);

        double dt = stepMinutes / MINUTES_PER_YEAR;
        double sqrtDt = Math.sqrt(dt);

        boolean useSchedule = sigmaSchedule != null && sigmaSchedule.length >= totalSteps;

        double[] driftPerStep;
        double[] diffPerStep;
        if (useSchedule) {
            driftPerStep = new double[totalSteps];
            diffPerStep = new double[totalSteps];
            for (int t = 0; t < totalSteps; t++) {
                double st = sigmaSchedule[t];
                driftPerStep[t] = (mu - 0.5 * st * st) * dt;
                diffPerStep[t] = st * sqrtDt;
            }
        } else {
            double constDrift = (mu - 0.5 * sigma * sigma) * dt;
            double constDiff = sigma * sqrtDt;
            driftPerStep = new double[totalSteps];
            diffPerStep = new double[totalSteps];
            for (int t = 0; t < totalSteps; t++) {
                driftPerStep[t] = constDrift;
                diffPerStep[t] = constDiff;
            }
        }

        double[][] paths = new double[evenPathCount][totalSteps + 1];
        SplittableRandom rng = new SplittableRandom();
        long startNano = System.nanoTime();

        for (int i = 0; i < evenPathCount; i += 2) {
            paths[i][0] = s0;
            paths[i + 1][0] = s0;
            for (int t = 1; t <= totalSteps; t++) {
                double z = rng.nextGaussian();
                if (fatTail) z = applyStudentT(z, nu, rng);
                double dr = driftPerStep[t - 1];
                double df = diffPerStep[t - 1];
                paths[i][t]     = paths[i][t - 1]     * Math.exp(dr + df * z);
                paths[i + 1][t] = paths[i + 1][t - 1] * Math.exp(dr + df * (-z));
            }
        }

        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
        log.debug("[PricePath] 생성 완료: paths={}, steps={}, sigma={:.4f}, fatTail={}, antithetic=true, mode={}, elapsed={}ms",
                evenPathCount, totalSteps, sigma, fatTail, useSchedule ? "GARCH" : "CONST", elapsedMs);

        return paths;
    }

    private double applyStudentT(double z, double nu, SplittableRandom rng) {
        int intNu = (int) nu;
        double chiSq = 0.0;

        int pairs = intNu / 2;
        if (pairs > 0) {
            double product = 1.0;
            for (int i = 0; i < pairs; i++) {
                product *= rng.nextDouble();
            }
            chiSq = -2.0 * Math.log(product);
        }

        if (intNu % 2 == 1) {
            double n = rng.nextGaussian();
            chiSq += n * n;
        }

        double t = z * Math.sqrt(nu / chiSq);
        return Double.isFinite(t) ? t : z;
    }

    private void validateRequest(SimulationRequest request) {
        if (request.getStartPrice() <= 0) {
            throw new IllegalArgumentException("시작 가격은 양수여야 합니다");
        }
        if (request.getSigma() <= 0) {
            throw new IllegalArgumentException("변동성(sigma)은 양수여야 합니다");
        }
        if (request.getPathCount() <= 0) {
            throw new IllegalArgumentException("경로 수(pathCount)는 양수여야 합니다");
        }
        if (request.getTimeStepMinutes() <= 0) {
            throw new IllegalArgumentException("시간 간격(timeStepMinutes)은 양수여야 합니다");
        }
        if (request.getHorizonMinutes() <= 0) {
            throw new IllegalArgumentException("시뮬레이션 기간(horizonMinutes)은 양수여야 합니다");
        }
        if (request.isUseFatTail() && request.getDegreesOfFreedom() <= 2) {
            throw new IllegalArgumentException("자유도(degreesOfFreedom)는 유한 분산을 위해 2보다 커야 합니다");
        }
    }
}
