package com.example.ricca.tesi;

import android.util.Log;

/**
 * Created by ricca on 16/09/2016.
 */
public class DataQueue {
    public final static int BUTTERWORTH = 0, LOWPASS = 1, FILTERED = 0, NONFILTERED = 1;
    private Data[] filteredDatas, nonFilteredDatas;
    private int length;
    private double[] butterworthCoefficients;
    private double lowPassCoefficient;
    private int type;
    private final static double THRESHOLD = 3.0f;

    public DataQueue() {
        filteredDatas = new Data[10];
        nonFilteredDatas = new Data[10];
        length = 0;
    }
    public int getLength(){
        return length;
    }

    /*
    type is the type of filter desired, parameter is the frequency ratio for the buttorth filter or the coefficient for the low-pass filter
     */
    public DataQueue(int type, double parameter) {
        this();
        if (type != BUTTERWORTH && type != LOWPASS) {
            throw new IllegalArgumentException();
        }
        this.type = type;
        switch (type) {
            case BUTTERWORTH: {
                butterworthCoefficients = new double[4];
                //1st and 3rd coefficients are equal, so the array is long 4
                double omega = 1.0 / (Math.tan(Math.PI * parameter));
                double sqr2 = Math.sqrt(2);
                butterworthCoefficients[0] = 1.0 / (1.0 + sqr2 * omega + omega * omega);
                butterworthCoefficients[1] = 2 * butterworthCoefficients[0];
                butterworthCoefficients[2] = 2.0 * (omega * omega - 1.0) * butterworthCoefficients[0];
                butterworthCoefficients[3] = -(1.0 - sqr2 * omega + omega * omega) * butterworthCoefficients[0];
                break;
            }
            case LOWPASS: {
                lowPassCoefficient = parameter;
                break;
            }
        }
    }

    /*
    adds and filters data to the queue
     */
    public synchronized void add(Data sample, float speed, double latitude, double longitude) {
        if (filteredDatas.length == length) {
            resize(FILTERED);
        }
        if (nonFilteredDatas.length == length) {
            resize(NONFILTERED);
        }

        //save the non-filtered data
        nonFilteredDatas[length] = new Data(sample, speed, latitude, longitude);


        //filter the data
        for (int axes = 0; axes < 3; axes++) {

            //LOW-PASS or BUTTERWORTH
            switch (type) {
                case LOWPASS: {
                    if (length != 0) {
                        sample.accelerations[axes] = (filteredDatas[length - 1].accelerations[axes] * lowPassCoefficient) + (1 - lowPassCoefficient) * sample.accelerations[axes];
                    }
                    break;
                }
                case BUTTERWORTH: {
                    if (length >= 2) {
                        sample.accelerations[axes] *= butterworthCoefficients[0];
                        sample.accelerations[axes] += butterworthCoefficients[1] * nonFilteredDatas[length-1].accelerations[axes]; //swap nonFilteredDatas with highFilteredDatas[first]
                        sample.accelerations[axes] += butterworthCoefficients[0] * nonFilteredDatas[length-2].accelerations[axes]; //swap nonFilteredDatas with highFilteredDatas[second]
                        sample.accelerations[axes] += butterworthCoefficients[2] * filteredDatas[length - 1].accelerations[axes];
                        sample.accelerations[axes] += butterworthCoefficients[3] * filteredDatas[length - 2].accelerations[axes];
                    }
                    break;
                }
            }
        }
        //add to filtered array
        filteredDatas[length++] = new Data(sample, speed, latitude, longitude);
    }

    private void resize(int array) {
        switch (array) {
            case FILTERED: {
                Data[] newDatas = new Data[(int)(filteredDatas.length * 1.1)];
                System.arraycopy(filteredDatas, 0, newDatas, 0, length);
                filteredDatas = newDatas;
                break;
            }
            case NONFILTERED: {
                Data[] newDatas = new Data[(int)(nonFilteredDatas.length * 1.1)];
                System.arraycopy(nonFilteredDatas, 0, newDatas, 0, length);
                nonFilteredDatas = newDatas;
                break;
            }
        }
    }

    public double[] getAccelerationArray(int filtered, int axis, int from, int length) {
        Data[] datas = null;
        switch (filtered) {
            case FILTERED: {
                datas = filteredDatas;
                break;
            }
            case NONFILTERED: {
                datas = nonFilteredDatas;
                break;
            }
        }
        if (from + length > datas.length) {
            return null;
        }
        double[] accelerations = new double[length];
        for (int i = from; i < from + length; i++) {
            accelerations[i - from] = datas[i].accelerations[axis];
        }
        return accelerations;
    }

    public double[] getAccelerationArray(int filtered, int axis) {
        if (filtered == DataQueue.FILTERED) {
            return getAccelerationArray(filtered, axis, 0, length);
        } else {
            return getAccelerationArray(filtered, axis, 0, length);
        }
    }

    public float[] getSpeed(){
        float[] speeds = new float[length];
        for(int i=0;i<length;i++){
            speeds[i] = filteredDatas[i].speed;
        }
        return speeds;
    }

    public Data getData(int index){
        return filteredDatas[index];
    }
}
