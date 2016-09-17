package com.example.ricca.tesi;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager manager;
    private Sensor accelerometer;
    private DataQueue[] queue;
    private Handler backgroundHandler;
    public final int SAMPLE_RATE = (int) 10E6;
    private HandlerThread backgroundThread;
    private boolean gathering;
    public final long SESSION_LENGTH = (long) 30000;
    CountDownTimer timer = new CountDownTimer(SESSION_LENGTH, SESSION_LENGTH) {
        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {
            stopGathering();
            saveExcel();
            startGathering();
        }
    };
    private int currentQueue;
    private final String[] DATA_FIELD_NAMES = {"FILTERED DATA", "NON FILTERED DATA", "REAL FFT PART", "IMAGINARY FFT PART"};

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
        queue[currentQueue].add(new Data(event));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void initialize() {
        for (int i = 0; i < queue.length; i++) {
            queue[i] = new DataQueue(DataQueue.BUTTERWORTH, 10);
        }
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
            stopGathering();
            saveExcel();
        } else {
            startGathering();
        }
    }

    // TODO: 16/09/2016 save the excel
    private void saveExcel() {
        HSSFWorkbook wb = new HSSFWorkbook();
        HSSFSheet currentSheet = wb.createSheet("sheet1");
        Row currentRow = currentSheet.createRow(0);
        for (int i = 0; i < 5; i++) {
            Cell currentCell = currentRow.createCell((3 * i) + 1);
            currentCell.setCellValue(DATA_FIELD_NAMES[i]);
        }
        for (int filtered = DataQueue.FILTERED; filtered < DataQueue.NONFILTERED; filtered++) {
            for (int axys = 0; axys < 3; axys++) {
                writeColumn(queue[currentQueue - 1].getAccelerations(filtered, axys), (3*filtered)+axys, currentSheet);
            }
        }
    }

    private void stopGathering() {
        timer.cancel();
        ((Button) findViewById(R.id.gatherButton)).setText("start data gathering");
        manager.unregisterListener(this);
        currentQueue = (currentQueue + 1) % 2;
        gathering = false;
    }

    private void startGathering() {
        ((Button) findViewById(R.id.gatherButton)).setText("stop data gathering");
        manager.registerListener(this, accelerometer, 10 * SAMPLE_RATE, backgroundHandler);
        gathering = true;
        timer.start();
    }

    private void writeColumn(double[] array, int columnNumber, HSSFSheet sheet) {
        if(columnNumber!=0){
            Row currentRow = sheet.getRow(1);
            switch (columnNumber%3){

            }
            for(int i=2;i<array.length+2;i++){

                currentRow = sheet.getRow(i);
        }
    }
}

