package com.example.ricca.tesi;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.formula.functions.T;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.jtransforms.fft.DoubleFFT_1D;

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
    public final long SESSION_LENGTH = (long) 60000;
    private final int FFT_LENGTH = 16, FFT_PADDING = 6;
    private final double ACCELERATION_THRESHOLD = 3.0f;
    CountDownTimer timer = new CountDownTimer(SESSION_LENGTH, SESSION_LENGTH) {
        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {
            stopGathering();
            saveExcel();
            System.gc();
            startGathering();
        }
    };
    private int currentQueue;
    private final String[] DATA_FIELD_NAMES = {"FILTERED DATA", "NON FILTERED DATA", "REAL FFT PART"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gathering) {
            stopGathering();
        }
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
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void initialize() {
        queue = new DataQueue[2];
        retrieveSensor();
        openBackgroundThread();
    }

    private void retrieveSensor() {
        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
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
            System.gc();
        } else {
            startGathering();
        }
    }

    private void saveExcel() {
        Log.i("tag", "started saving");
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
        int saveQueue = (currentQueue == 0) ? 1 : 0;
        for (int filtered = DataQueue.FILTERED; filtered <= DataQueue.NONFILTERED; filtered++) {
            for (int axes = 0; axes < 3; axes++) {
                double[] datas = queue[(saveQueue)].getAccelerationArray(filtered, axes);
                writeColumn(datas, (3 * filtered) + axes, currentSheet, false);
                if (axes == 2 && filtered == DataQueue.FILTERED) {
                    int columnIndex=0;
                    double[][] FFTs = calculateDriftingFFT(datas, queue[saveQueue].getLength());
                    for(int i=0;i<FFTs.length;i++){
                        if(FFTs[i]!= null){
                            writeColumn(FFTs[i], FFT_PADDING+columnIndex++, currentSheet, true);
                        }
                    }
                    //datas = calculateFFT(datas, queue[saveQueue].getLength());
                    //writeColumn(datas, FFT_PADDING, currentSheet, true);
                }
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
        MediaScannerConnection.scanFile(this, new String[]{file.getPath()}, null, null);
        queue[saveQueue] = null;
        Log.i("tag", "done saving");
    }

    private void stopGathering() {
        timer.cancel();
        ((Button) findViewById(R.id.gatherButton)).setText("start data gathering");
        manager.unregisterListener(this);
        currentQueue = (currentQueue + 1) % queue.length;
        gathering = false;
    }

    private void startGathering() {
        queue[currentQueue] = new DataQueue(DataQueue.BUTTERWORTH, 0.1);
        ((Button) findViewById(R.id.gatherButton)).setText("stop data gathering");
        manager.registerListener(this, accelerometer, 5 * SAMPLE_RATE, backgroundAccelerometerHandler);
        gathering = true;
        timer.start();
    }

    private double[] calculateFFT(double[] array, int length) {
        if (length < FFT_LENGTH) {
            return null;
        }
        DoubleFFT_1D fft = new DoubleFFT_1D(FFT_LENGTH);
        //get the max of the array
        double max = Math.abs(array[0]);
        int maxIndex = 0;
        for (int i = 1; i < length; i++) {
            if (Math.abs(array[i]) >= max) {
                max = Math.abs(array[i]);
                maxIndex = i;
            }
        }
        //fft it
        double[] fftArray = new double[2 * FFT_LENGTH];
        System.arraycopy(array, maxIndex - (FFT_LENGTH / 2), fftArray, 0, FFT_LENGTH);
        fft.realForwardFull(fftArray);
        double[] fftWithIndex = new double[fftArray.length + 1];
        System.arraycopy(fftArray, 0, fftWithIndex, 0, fftArray.length);
        fftWithIndex[fftWithIndex.length - 1] = maxIndex+1;
        return fftWithIndex;
    }

    private double[][] calculateDriftingFFT(double[] array, int length) {
        if (length < FFT_LENGTH) {
            return null;
        }
        double[][] returnArray = new double[array.length][];
        for(int i=0;i<returnArray.length;i++){
            returnArray[i] = null;
        }
        int returnArrayIndex = 0;
        DoubleFFT_1D fft = new DoubleFFT_1D(FFT_LENGTH);
        for (int i = (FFT_LENGTH / 2); i < array.length-(FFT_LENGTH/2)+1; i++) {
            if (Math.abs(array[i]) > ACCELERATION_THRESHOLD) {
                double[] fftArray = new double[2 * FFT_LENGTH];
                System.arraycopy(array, i - (FFT_LENGTH / 2), fftArray, 0, FFT_LENGTH);
                fft.realForwardFull(fftArray);
                double[] fftWithIndex = new double[fftArray.length + 1];
                System.arraycopy(fftArray, 0, fftWithIndex, 0, fftArray.length);
                fftWithIndex[fftWithIndex.length - 1] = i+1;
                returnArray[returnArrayIndex++] = fftWithIndex;
            }
        }
        return returnArray;
    }

    private void writeColumn(double[] array, int columnNumber, HSSFSheet sheet, boolean isFFT) {
        try {
            if (!isFFT) {
                //print the axis on top of the column
                String value = "";
                switch (columnNumber % 3) {
                    case 0: {
                        value = "X-axis";
                        break;
                    }
                    case 1: {
                        value = "Y-axis";
                        break;
                    }
                    case 2: {
                        value = "Z-axis";
                        break;
                    }
                }
                Method method = createCell(sheet, 1, columnNumber, value);
                //print the values
                for (int i = 0; i < array.length; i++) {
                    createCell(sheet, i + 2, columnNumber, array[i], method);
                    /*
                    currentRow = (Row) method.invoke(sheet, i + 2);
                    currentCell = currentRow.createCell(columnNumber);
                    currentCell.setCellValue(array[i]);*/
                }
            } else {
                //print the axis on top of the column
                String value = "Z-axis";
                Method method = createCell(sheet, 1, columnNumber, value);
                int i;
                for (i = 0; i < array.length - 1; i = i + 2) {
                    createCell(sheet, (i / 2) + 2, columnNumber, array[i], method);/*
                    currentRow = (Row) method.invoke(sheet, i + 2);
                    currentCell = currentRow.createCell(columnNumber);
                    currentCell.setCellValue(array[i]);*/
                }
                createCell(sheet, (i / 2) + 2, columnNumber, array[array.length - 1], method);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /*
    inserts a value in the selected cell
     */
    private <T> void createCell(Sheet sheet, int rowNumber, int columnNumber, T value, Method method) throws InvocationTargetException, IllegalAccessException {
        Row currentRow = (Row) method.invoke(sheet, rowNumber);
        Cell currentCell = currentRow.createCell(columnNumber);
        if (value instanceof String) {
            currentCell.setCellValue((String) value);
        } else {
            currentCell.setCellValue((Double) value);
        }
    }


    private <T> Method createCell(Sheet sheet, int rowNumber, int columnNumber, T value) throws InvocationTargetException, IllegalAccessException {
        Method method = getMethod(columnNumber);
        createCell(sheet, rowNumber, columnNumber, value, method);
        return method;
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
        int month = calendar.get(Calendar.MONTH) + 1;
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

