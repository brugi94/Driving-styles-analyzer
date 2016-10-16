package com.example.ricca.tesi;

/**
 * Created by ricca on 04/10/2016.
 */
public class dataEvaluator {
    private float startSpeed, currentCalculatedSpeed, oldSpeed;
    private final double NOT_SAFE_THRESHOLD = 40; //brake event's power must be higher than this (which is the power a brake from 50km/h to 30km/h over 3 seconds generates)
    private double totalTimeDelta;
    private double oldSample;

    public dataEvaluator() {
    }

    public evaluateResult evaluate(double sample, float speed, double timeDelta) {
        if (oldSample >= 0) {
            if (startSpeed == 0 && oldSpeed != 0) {
                startSpeed = currentCalculatedSpeed = oldSpeed;
            }
            if (startSpeed != 0) {
                totalTimeDelta += timeDelta;
                double deltaV = -oldSample * timeDelta;
                currentCalculatedSpeed += deltaV;
                double currentEnergyDelta = Math.pow(currentCalculatedSpeed, 2) - Math.pow(startSpeed, 2);
                double powerDelta = currentEnergyDelta / totalTimeDelta;

                //update values
                oldSample = sample;
                oldSpeed = speed;
                if (powerDelta <= 0 * NOT_SAFE_THRESHOLD) {
                    return new evaluateResult(powerDelta, evaluateResult.NOT_SAFE);
                } else {
                    return new evaluateResult(powerDelta, evaluateResult.SAFE);
                }
            }
        }
        oldSample = sample;
        oldSpeed = speed;
        startSpeed = 0;
        currentCalculatedSpeed = 0;
        totalTimeDelta = 0;
        return new evaluateResult(0, evaluateResult.SAFE);
    }
}
