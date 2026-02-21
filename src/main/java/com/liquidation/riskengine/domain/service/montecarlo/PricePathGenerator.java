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

        double dt = stepMinutes / MINUTES_PER_YEAR;
        double drift = (mu - 0.5 * sigma * sigma) * dt;
        double diffusion = sigma * Math.sqrt(dt);

        double[][] paths = new double[pathCount][totalSteps + 1];
        SplittableRandom rng = new SplittableRandom();

        long startNano = System.nanoTime();

        for (int i = 0; i < pathCount; i++) {
            paths[i][0] = s0;
            for (int t = 1; t <= totalSteps; t++) {
                double z = rng.nextGaussian();
                if (fatTail) {
                    z = applyStudentT(z, nu, rng);
                }
                paths[i][t] = paths[i][t - 1] * Math.exp(drift + diffusion * z);
            }
        }

        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
        log.debug("[PricePath] 생성 완료: paths={}, steps={}, sigma={:.4f}, fatTail={}, elapsed={}ms",
                pathCount, totalSteps, sigma, fatTail, elapsedMs);

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
            throw new IllegalArgumentException("startPrice must be positive");
        }
        if (request.getSigma() <= 0) {
            throw new IllegalArgumentException("sigma must be positive");
        }
        if (request.getPathCount() <= 0) {
            throw new IllegalArgumentException("pathCount must be positive");
        }
        if (request.getTimeStepMinutes() <= 0) {
            throw new IllegalArgumentException("timeStepMinutes must be positive");
        }
        if (request.getHorizonMinutes() <= 0) {
            throw new IllegalArgumentException("horizonMinutes must be positive");
        }
        if (request.isUseFatTail() && request.getDegreesOfFreedom() <= 2) {
            throw new IllegalArgumentException("degreesOfFreedom must be > 2 for finite variance");
        }
    }
}
