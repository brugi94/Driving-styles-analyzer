package com.example.ricca.tesi;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager manager;
    private Sensor accelerometer;
    private DataQueue[] queue;
    private Handler backgroundAccelerometerHandler, backgroundExcelHandler;
    public final int SAMPLE_RATE = (int) 10E3;
    private HandlerThread backgroundAccelerometerThread, backgroundExcelThread;
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
    protected void onPause() {
        super.onPause();
        closeBackgroundThread();
    }

    private void closeBackgroundThread() {
        backgroundAccelerometerThread.quitSafely();
        backgroundExcelThread.quitSafely();
        try {
            backgroundExcelThread.join();
            backgroundAccelerometerThread.join();
            backgroundAccelerometerThread = null;
            backgroundAccelerometerHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initialize();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        queue[currentQueue].add(new Data(event));
        Log.i("tag", "data acquired");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void initialize() {
        queue = new DataQueue[2];
        for (int i = 0; i < queue.length; i++) {
            queue[i] = new DataQueue(DataQueue.BUTTERWORTH, 0.10);
        }
        retrieveSensor();
        openBackgroundThread();
    }

    private void retrieveSensor() {
        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private void openBackgroundThread() {
        backgroundExcelThread = new HandlerThread(("background excel thread"));
        backgroundExcelThread.start();
        backgroundExcelHandler = new Handler(backgroundExcelThread.getLooper());
        backgroundAccelerometerThread = new HandlerThread("background worker thread");
        backgroundAccelerometerThread.start();
        backgroundAccelerometerHandler = new Handler(backgroundAccelerometerThread.getLooper());
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
        //create the sheet

        HSSFWorkbook wb = new HSSFWorkbook();
        HSSFSheet currentSheet = wb.createSheet("sheet1");

        //print names for the datas

        Row currentRow = currentSheet.createRow(0);
        for (int i = 0; i < DATA_FIELD_NAMES.length; i++) {
            Cell currentCell = currentRow.createCell((3 * i) + 1);
            currentCell.setCellValue(DATA_FIELD_NAMES[i]);
        }


        //print the filtered and non-filtered datas

        for (int filtered = DataQueue.FILTERED; filtered <= DataQueue.NONFILTERED; filtered++) {
            for (int axys = 0; axys < 3; axys++) {
                int saveQueue = (currentQueue==0)? 1:0;
                writeColumn(queue[(saveQueue)].getAccelerations(filtered, axys), (3 * filtered) + axys, currentSheet);
            }
        }

        //save the excel
        File file = getFile();
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            wb.write(outputStream);
            outputStream.close();
            wb.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopGathering() {
        timer.cancel();
        ((Button) findViewById(R.id.gatherButton)).setText("start data gathering");
        manager.unregisterListener(this);
        currentQueue = (currentQueue + 1) % queue.length;
        gathering = false;
    }

    private void startGathering() {
        ((Button) findViewById(R.id.gatherButton)).setText("stop data gathering");
        manager.registerListener(this, accelerometer, 5*SAMPLE_RATE, backgroundAccelerometerHandler);
        gathering = true;
        timer.start();
    }

    private void writeColumn(double[] array, int columnNumber, HSSFSheet sheet) {
        try {
            //print the axys on top of the column
            Method method = getMethod(columnNumber);
            Row currentRow = (Row) method.invoke(sheet, 1);
            Cell currentCell = currentRow.createCell(columnNumber);
            switch (columnNumber % 3) {
                case 0: {
                    currentCell.setCellValue("X-axys");
                    break;
                }
                case 1: {
                    currentCell.setCellValue("Y-axys");
                    break;
                }
                case 2: {
                    currentCell.setCellValue("Z-axys");
                    break;
                }
            }
            //print the values
            for (int i = 2; i < array.length + 2; i++) {
                currentRow = (Row) method.invoke(sheet, i);
                currentCell = currentRow.createCell(columnNumber);
                currentCell.setCellValue(array[i - 2]);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /*
    returns getRow or createRow method properly
     */
    private Method getMethod(int columnNumber) {
        Method returnMethod = null;
        try {
            Class<?> c = Class.forName(Sheet.class.getName());
            String methodName = "";
            if (columnNumber != 0) {
                methodName = "getRow";
            } else {
                methodName = "createRow";
            }
            returnMethod = c.getMethod(methodName, int.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return returnMethod;
    }

    //returns a proper file for saving the excel
    private File getFile() {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int i = 0;
        File returnFile = null;
        while (true) {
            String name = "tesi-" + day + "-" + month + "-" + year + "-" + i + ".xls";
            returnFile = new File(folder.getPath(), name);
            if (!returnFile.exists()) {
                break;
            }
            i++;
        }
        return returnFile;
    }
}

