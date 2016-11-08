package com.example.ricca.tesi;

/**
 * Created by ricca on 04/10/2016.
 */
public class dataEvaluator {
    private float startSpeed, currentCalculatedSpeed;
    private float oldSpeed;
    private final double NOT_SAFE_THRESHOLD = 80; //brake event's power must be higher than this (which is the power a brake from 50km/h to 30km/h over 1.5 seconds generates)
    private double totalTimeDelta;
    private double[] oldSamples; //index 0 is the oldest

    public dataEvaluator() {
        oldSamples = new double[2];
    }

    public evaluateResult evaluate(double sample, float speed, double timeDelta) {
        double powerDelta = 0;
        //if we go from accelerating to decelerating or vice versa update start speed and restart timer
        if (oldSamples[0] * oldSamples[1] < 0) {
            startSpeed = oldSpeed;
            currentCalculatedSpeed = oldSpeed;
            totalTimeDelta = 0;
        }
        totalTimeDelta += timeDelta;
        double deltaV = -oldSamples[1] * timeDelta;
        currentCalculatedSpeed += deltaV;
        //if we go below 0, count it as 0
        currentCalculatedSpeed = (currentCalculatedSpeed > 0) ? currentCalculatedSpeed : 0;
        double currentEnergyDelta = Math.pow(currentCalculatedSpeed, 2) - Math.pow(startSpeed, 2);
        powerDelta = currentEnergyDelta / totalTimeDelta;

        updateValues(sample, speed);
        evaluateResult returnResult = new evaluateResult();
        returnResult.powerDelta = Double.isInfinite(powerDelta) || Double.isNaN(powerDelta) ? 0 : powerDelta;
        return new evaluateResult(Double.isInfinite(powerDelta) || Double.isNaN(powerDelta) ? 0 : powerDelta, (Math.abs(powerDelta) > NOT_SAFE_THRESHOLD) ? evaluateResult.NOT_SAFE : evaluateResult.SAFE);

    }

    private void updateValues(double sample, float speed) {
        oldSpeed = speed;
        oldSamples[0] = oldSamples[1];
        oldSamples[1] = sample;
    }
}
//        if (oldSample <= 0) {
//            if (startSpeed == 0 && oldSpeed != 0) {
//                startSpeed = currentCalculatedSpeed = oldSpeed;
//            }
//            if (startSpeed != 0) {
//                totalTimeDelta += timeDelta;
//                double deltaV = oldSample * timeDelta;
//                currentCalculatedSpeed += deltaV;
//                double currentEnergyDelta = Math.pow(currentCalculatedSpeed, 2) - Math.pow(startSpeed, 2);
//                double powerDelta = currentEnergyDelta / totalTimeDelta;
//
//                //update values
//                oldSample = sample;
//                oldSpeed = speed;
//                if (powerDelta <= NOT_SAFE_THRESHOLD) {
//                    return new evaluateResult(powerDelta, evaluateResult.NOT_SAFE);
//                } else {
//                    return new evaluateResult(powerDelta, evaluateResult.SAFE);
//                }
//            }
//        }
//        oldSample = sample;
//        oldSpeed = speed;
//        startSpeed = 0;
//        currentCalculatedSpeed = 0;
//        totalTimeDelta = 0;
//        return new evaluateResult(0, evaluateResult.SAFE);
