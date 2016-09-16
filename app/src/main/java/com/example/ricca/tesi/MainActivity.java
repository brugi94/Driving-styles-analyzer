package com.example.ricca.tesi;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager manager;
    private Sensor accelerometer;
    private DataQueue queue;
    private Handler backgroundHandler;
    public final int SAMPLE_RATE = (int) 10E6;
    private HandlerThread backgroundThread;
    private boolean gathering;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    @Override
    protected void onResume() {
        super.onResume();
        initialize();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        queue.add(new Data(event));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void initialize() {
        queue = new DataQueue(DataQueue.BUTTERWORTH,10);
        retrieveSensor();
        openBackgroundThread();
    }

    private void retrieveSensor() {
        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = manager.getDefaultSensor(SensorManager.SENSOR_ACCELEROMETER);
    }

    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("background worker thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void toggleGathering(View view) {
        if (gathering) {
            ((Button) findViewById(R.id.gatherButton)).setText("stop data gathering");
            manager.unregisterListener(this);
            saveExcel();
            gathering = false;
        } else {
            ((Button) findViewById(R.id.gatherButton)).setText("start data gathering");
            manager.registerListener(this, accelerometer, 10 * SAMPLE_RATE, backgroundHandler);
            gathering = true;
        }
    }

    // TODO: 16/09/2016 save the excel
    private void saveExcel(){
        HSSFWorkbook wb = new HSSFWorkbook();
        wb.createSheet("sheet1");
    }
}

