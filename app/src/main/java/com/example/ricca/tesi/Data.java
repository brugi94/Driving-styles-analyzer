package com.example.ricca.tesi;

import android.hardware.SensorEvent;

/**
 * Created by ricca on 16/09/2016.
 */
public class Data {
    public double[] accelerations;
    public float speed;
    public double latitude, longitude;
    public Data(){
        accelerations = new double[3];
    }
    public Data(SensorEvent datas){
        this();
        for(int i=0;i<3;i++){
            accelerations[i] = datas.values[i];
        }
    }
    public Data(Data input, float speed, double latitude, double longitude){
        this();
        this.speed = speed;
        this.latitude = latitude;
        this.longitude = longitude;
        for(int axys=0;axys<3;axys++){
            this.accelerations[axys] = input.accelerations[axys];
        }
    }
}
