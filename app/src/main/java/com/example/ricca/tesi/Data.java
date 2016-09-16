package com.example.ricca.tesi;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

/**
 * Created by ricca on 16/09/2016.
 */
public class Data {
    public double[] accelerations;
    public Data(){
        accelerations = new double[3];
    }
    public Data(SensorEvent datas){
        if(datas.sensor.getType()!= Sensor.TYPE_ACCELEROMETER){
            throw new IllegalArgumentException();
        }
        for(int i=0;i<3;i++){
            accelerations[i] = datas.values[i];
        }
    }
}
