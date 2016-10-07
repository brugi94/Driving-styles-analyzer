package com.example.ricca.tesi;

/**
 * Created by ricca on 04/10/2016.
 */
public class evaluateResult {
    public final static int NOT_SAFE = 1, SAFE = 0, NOT_SAFE_LOW_SPEED = 2; //return values from the method evaluate
    public int length, safetyValue;

    public evaluateResult(int length, int safetyValue){
        this.length = length;
        this.safetyValue = safetyValue;
    }
}
