package com.example.ricca.tesi;

import android.util.Log;

/**
 * Created by ricca on 16/09/2016.
 */
public class DataQueue {
    public final static int BUTTERWORTH = 0, LOWPASS = 1, FILTERED = 0, NONFILTERED = 1;
    private Data[] filteredDatas, nonFilteredDatas;
    private int length;
    private double[] gravity;
    private double[] butterworthCoefficients;
    private double lowPassCoefficient;
    private float highPassCoefficient;
    private Data[] highFilteredDatas;
    private int type, currentLastData;

    public DataQueue() {
        filteredDatas = new Data[10];
        nonFilteredDatas = new Data[10];
        length = 0;
        gravity = new double[3];
        highPassCoefficient = 0.1F;
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
                highFilteredDatas = new Data[3];
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

    public DataQueue(int type, double parameter, double[] currentGravity) {
        this(type, parameter);
        for (int axys = 0; axys < 3; axys++) {
            gravity[axys] = currentGravity[axys];
        }
    }

    public synchronized void add(Data sample) {
        if (filteredDatas.length == length) {
            resize(FILTERED);
        }
        if (nonFilteredDatas.length == length) {
            resize(NONFILTERED);
        }

        //save the non-filtered data
        nonFilteredDatas[length] = new Data(sample);


        //filter the data
        for (int axys = 0; axys < 3; axys++) {

            //HIGH-PASS filter
            gravity[axys] = (highPassCoefficient * sample.accelerations[axys]) + (1 - highPassCoefficient) * gravity[axys];
            sample.accelerations[axys] -= gravity[axys];
            //we use highFilteredDatas to keep the last 2 values of datas that have been high filtered (will need them when butterworth filtering)
            if ((axys == 2) && (type==BUTTERWORTH)) {
                highFilteredDatas[currentLastData] = new Data(sample);
            }
            //LOW-PASS or BUTTERWORTH
            switch (type) {
                case LOWPASS: {
                    if (length != 0) {
                        sample.accelerations[axys] = (filteredDatas[length - 1].accelerations[axys] * lowPassCoefficient) + (1 - lowPassCoefficient) * sample.accelerations[axys];
                    }
                    break;
                }
                case BUTTERWORTH: {
                    if (length >= 2) {
                        //calculate which highfiltereddatas i need to use (using a circular array to save them)
                        int first, second;
                        switch (currentLastData) {
                            case 0: {
                                first = 2;
                                second = 1;
                                break;
                            }
                            case 1: {
                                first = 0;
                                second = 2;
                                break;
                            }
                            default: {
                                first = currentLastData - 1;
                                second = currentLastData - 2;
                            }
                        }
                        sample.accelerations[axys] *= butterworthCoefficients[0];
                        sample.accelerations[axys] += butterworthCoefficients[1] * highFilteredDatas[first].accelerations[axys];
                        sample.accelerations[axys] += butterworthCoefficients[0] * highFilteredDatas[second].accelerations[axys];
                        sample.accelerations[axys] += butterworthCoefficients[2] * filteredDatas[length - 1].accelerations[axys];
                        sample.accelerations[axys] += butterworthCoefficients[3] * filteredDatas[length - 2].accelerations[axys];
                    }
                    break;
                }
            }
            if (axys == 2) {
                currentLastData = (currentLastData + 1) % 3;
            }
        }
        //add to filtered array
        filteredDatas[length++] = new Data(sample);
    }

    private void resize(int array) {
        switch (array) {
            case FILTERED: {
                Data[] newDatas = new Data[filteredDatas.length * 2];
                System.arraycopy(filteredDatas, 0, newDatas, 0, length);
                filteredDatas = newDatas;
                break;
            }
            case NONFILTERED: {
                Data[] newDatas = new Data[nonFilteredDatas.length * 2];
                System.arraycopy(nonFilteredDatas, 0, newDatas, 0, length);
                nonFilteredDatas = newDatas;
                break;
            }
        }
    }

    // TODO: 16/09/2016 method that receives the axys and returns an array with all the datas of that axys
    public double[] getAccelerations(int filtered, int axys) {
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
        double[] accelerations = new double[length];
        for (int i = 0; i < length; i++) {
            accelerations[i] = datas[i].accelerations[axys];
        }
        return accelerations;
    }

    public double[] getGravity() {
        return gravity;
    }
}
