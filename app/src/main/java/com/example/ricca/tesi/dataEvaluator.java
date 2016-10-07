package com.example.ricca.tesi;

/**
 * Created by ricca on 04/10/2016.
 */
public class dataEvaluator {
    private float startSpeed;
    private int notSafeCount;
    private final float NOT_SAFE_TIME_THRESHOLD = 0.25f; //brake event must be at least 1s long
    private final float SPEED_THRESHOLD = 8.3f; // more or less 30km/h
    private final double ACCELERATION_THRESHOLD = 3.0f;
    private int notSafeThreshold;

    public dataEvaluator(double sampleRate) {
        notSafeThreshold = (int) ((NOT_SAFE_TIME_THRESHOLD / sampleRate) * 10E5);
        notSafeCount = 0;
    }

    public evaluateResult evaluate(double sample, float speed) {
        if (Math.abs(sample) <= -ACCELERATION_THRESHOLD) {
            if (startSpeed != 0 && speed != 0) {
                startSpeed = speed;
            }
            notSafeCount++;
            if (notSafeCount >= notSafeThreshold) {
                if (startSpeed >= SPEED_THRESHOLD) {
                    return new evaluateResult(notSafeCount, evaluateResult.NOT_SAFE);
                } else {
                    return new evaluateResult(notSafeCount, evaluateResult.NOT_SAFE_LOW_SPEED);
                }
            }
        } else {
            startSpeed = 0;
            notSafeCount = 0;
        }
        return new evaluateResult(notSafeCount, evaluateResult.SAFE);
    }
}
