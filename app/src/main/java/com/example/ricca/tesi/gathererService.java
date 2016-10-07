package com.example.ricca.tesi;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by ricca on 03/10/2016.
 */
public class gathererService extends Service implements SensorEventListener, LocationListener {
    private SensorManager manager;
    private Sensor accelerometer;
    private DataQueue queue;
    private Handler backgroundAccelerometerHandler;
    public final int SAMPLE_RATE = (int) 10E3, LATITUDE_COLUMN = 5, LONGITUDE_COLUMN = 6, EVENT_TYPE_COLUMN = 3, TIMESTAMP_COLUMN = 4, NO_LOCATION_EVENT = -1;
    private HandlerThread backgroundAccelerometerThread;
    private boolean isGathering, isSaving;
    private int rowNumber = 2, oldEventSafety;
    private final String SUDDEN_BRAKE = "SUDDEN BRAKE";
    private final String[] DATA_FIELD_NAMES = {"FILTERED DATA"}, SECOND_ROW_STRINGS = {"x-axis", "y-axis", "z-axis", "event type", "timestamp", "latitude", "longitude"};
    private ArrayList<Integer> RecordedIndexes;
    private ArrayList<String> RecordedEvents;
    private ArrayList<Date> recordedTimeStamps;
    private LocationManager locationManager;
    private float currentSpeed;
    private double currentLatitude, currentLongitude;
    private dataEvaluator evaluator;
    private CellStyle redFormat, orangeFormat, dateFormat;
    private HSSFWorkbook wb;
    private HSSFSheet sheet;
    private MediaPlayer player;
    private String currentFileName;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isSaving) {
            //if has int
            if (intent.hasExtra(MainActivity.INTENT_INT_TAG)) {
                switch (intent.getIntExtra(MainActivity.INTENT_INT_TAG, -1)) {
                    case MainActivity.EVENT_TOGGLE: {
                        synchronized (this) {
                            if (isGathering) {
                                stopGathering();
                                saveExcel();
                                System.gc();
                            } else {
                                startGathering();
                            }
                            break;
                        }
                    }
                    case MainActivity.EVENT_INDEX: {
                        if (isGathering) {
                            RecordedIndexes.add(queue.getLength());
                            recordedTimeStamps.add(new Date());
                            break;
                        }
                    }
                }
            }
            //if it has strings
            if (intent.hasExtra(MainActivity.INTENT_STRING_TAG)) {
                if (isGathering) {
                    RecordedEvents.add(intent.getStringExtra(MainActivity.INTENT_STRING_TAG));
                }
            }
        }
        return START_STICKY;
    }

    /*
    registers the listeners and logs the gathering start event
     */
    private void startGathering() {
        initialize();
        manager.registerListener(this, accelerometer, 5 * SAMPLE_RATE, backgroundAccelerometerHandler);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(this, "no permissions", Toast.LENGTH_SHORT);
            return;
        }
        logEvent(NO_LOCATION_EVENT, new Date(), "gathering started");

        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        isGathering = true;
    }

    /*
    sets up the fields
     */
    private void initialize() {
        if (evaluator == null) {
            evaluator = new dataEvaluator(5 * SAMPLE_RATE);
        }

        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(getApplicationContext(), notification);
            player.setLooping(true);
            player.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
        queue = new DataQueue(DataQueue.BUTTERWORTH, 0.1);
        recordedTimeStamps = new ArrayList<Date>();
        RecordedIndexes = new ArrayList<Integer>();
        RecordedEvents = new ArrayList<String>();

        rowNumber = 2;
        setupExcel();

        retrieveSensor();
        openBackgroundThread();
    }

    /*
    creates the workbook, a sheet in it and prints some values
     */
    private void setupExcel() {
        //create the sheet

        wb = new HSSFWorkbook();
        sheet = wb.createSheet("sheet1");

        //setup dateFormat style
        CreationHelper createHelper = wb.getCreationHelper();
        dateFormat = wb.createCellStyle();
        dateFormat.setDataFormat(createHelper.createDataFormat().getFormat("m/d/yy h:mm"));

        //print names for the datas
        Row currentRow = sheet.createRow(0);
        for (int i = 0; i < DATA_FIELD_NAMES.length; i++) {
            Cell currentCell = currentRow.createCell((3 * i) + 1);
            currentCell.setCellValue(DATA_FIELD_NAMES[i]);
        }
        //print the strings on the second row
        currentRow = sheet.createRow(1);
        for (int i = 0; i < SECOND_ROW_STRINGS.length; i++) {
            Cell currentCell = currentRow.createCell(i);
            currentCell.setCellValue(SECOND_ROW_STRINGS[i]);
        }
    }


    private void retrieveSensor() {
        if (locationManager == null) {
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(getApplicationContext(), "gps not allowed", Toast.LENGTH_LONG);
            }
        }
        if (manager == null) {
            manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if ((accelerometer = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)) == null) {
                Toast.makeText(getApplicationContext(), "linear acceleration not supported", Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        }
    }

    private void openBackgroundThread() {
        if (backgroundAccelerometerHandler == null) {
            backgroundAccelerometerThread = new HandlerThread("background worker thread");
            backgroundAccelerometerThread.start();
            backgroundAccelerometerHandler = new Handler(backgroundAccelerometerThread.getLooper());
        }
    }

    /*
    when a new sample is received, it's added to the queue, printed on the excel document and logged if needed
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        queue.add(new Data(event), currentSpeed, currentLatitude, currentLongitude);
        analyzeData(queue.getData(queue.getLength() - 1));
    }

    /*
    evaluates the sample's safety, prints the sample on excel and depending on the safety of the sample will take some actions:
    1)if it's risky an alarm will be started, the event will be logged and the sample's cell background will be red
    2)if it's risky but the speed is low, the sample's cell background will be orange
    3)if it's safe, the sample is printed on the excel
     */
    private void analyzeData(Data sample) {


        //evaluate the acquired sample
        evaluateResult result = evaluator.evaluate(sample.accelerations[2], sample.speed);

        //print the values on the excel
        try {
            for (int i = 0; i < 3; i++) {
                //if it's the Z-axis
                if (i != 2) {
                    //create not colored cell
                    createCell(sheet, rowNumber, i, sample.accelerations[i], wb, null);
                } else {
                    //create colored cell based on result
                    createCell(sheet, rowNumber, i, sample.accelerations[i], wb, result);
                }
            }

            //if it's different from the old event
            if (result.safetyValue != oldEventSafety) {

                //if it's not safe log the event and sound an alarm
                if (result.safetyValue == evaluateResult.NOT_SAFE || currentSpeed != 0) {
                    if (result.safetyValue == evaluateResult.NOT_SAFE) {
                        logEvent(queue.getLength() - 1, new Date(), SUDDEN_BRAKE);
                    }
                    if (!player.isPlaying()) {
                        player.start();
                        //stop it after 10 seconds
                        new CountDownTimer(6000, 6000) {
                            @Override
                            public void onTick(long millisUntilFinished) {

                            }

                            @Override
                            public void onFinish() {
                                player.stop();
                                try {
                                    player.prepare();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                }

                /* if it's different from safe, print a record on the excel sheet
                if (result.safetyValue != evaluateResult.SAFE) {
                    createCell(sheet, rowNumber, LATITUDE_COLUMN, sample.latitude, wb, null);
                    createCell(sheet, rowNumber, LONGITUDE_COLUMN, sample.longitude, wb, null);
                    createCell(sheet, rowNumber, TIMESTAMP_COLUMN, new Date(), wb, null);
                */
            }
            //update old event value
            oldEventSafety = result.safetyValue;
            rowNumber++;

        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    /*
    stops receiving updates and logs the event "gathering stopped"
     */
    private void stopGathering() {
        if (isGathering) {
            manager.unregisterListener(this);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            locationManager.removeUpdates(this);
            logEvent(NO_LOCATION_EVENT, new Date(), "gathering stopped");
            isGathering = false;
        }
    }

    /*
    saves the excel document in the default download directory
     */
    private void saveExcel() {
        isSaving = true;
        Log.i("tag", "started saving");


        printEvents();

        Log.i("tag", "write");

        //save the excel
        String outputFileName = getFileName();
        File outputFile = null;
        try {
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            outputFile = new File(folder, outputFileName + ".xls");
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            wb.write(outputStream);
            outputStream.close();
            wb.close();
            MediaScannerConnection.scanFile(this, new String[]{outputFile.getPath()}, null, null);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        queue = null;
        Log.i("tag", "done saving");
        isSaving = false;
        currentFileName = null;

    }

    /*
    prints the recorded events on the excel sheet
     */
    private void printEvents() {
        //find length of events array
        int minLength = Math.min(RecordedIndexes.size(), recordedTimeStamps.size());
        minLength = Math.min(minLength, RecordedEvents.size());
        //for each event registered
        for (int rowIndex = 0; rowIndex < minLength; rowIndex++) {
            //find the row with its index
            Row currentRow = this.sheet.getRow(RecordedIndexes.get(rowIndex) + 2);
            if (currentRow == null) {
                currentRow = this.sheet.createRow(RecordedIndexes.get(rowIndex) + 2);
            }
            Cell currentCell = currentRow.createCell(EVENT_TYPE_COLUMN);
            //print type of event
            currentCell.setCellValue(RecordedEvents.get(rowIndex));
            //print dateFormat
            currentCell = currentRow.createCell(TIMESTAMP_COLUMN);
            currentCell.setCellValue(recordedTimeStamps.get(rowIndex));
            currentCell.setCellStyle(dateFormat);
            //print latitude
            currentCell = currentRow.createCell(LATITUDE_COLUMN);
            int j = RecordedIndexes.get(rowIndex);
            Data datas = queue.getData(j);
            currentCell.setCellValue(datas.latitude);
            //print altitude
            currentCell = currentRow.createCell(LONGITUDE_COLUMN);
            currentCell.setCellValue(queue.getData(RecordedIndexes.get(rowIndex)).longitude);
        }
    }

    private void closeBackgroundThread() {
        backgroundAccelerometerThread.quitSafely();
        try {
            backgroundAccelerometerThread.join();
            backgroundAccelerometerThread = null;
            backgroundAccelerometerHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        closeBackgroundThread();
        stopGathering();
    }

    /*
    inserts a value in the selected cell
     */
    private <T> void createCell(HSSFSheet sheet, int rowNumber, int columnNumber, T value, Method method, HSSFWorkbook wb, evaluateResult result) throws InvocationTargetException, IllegalAccessException {
        Row currentRow = (Row) method.invoke(sheet, rowNumber);
        Cell currentCell = currentRow.createCell(columnNumber);
        if (value instanceof String) {
            currentCell.setCellValue((String) value);
        }
        if (value instanceof Double) {
            currentCell.setCellValue((Double) value);
            //color the cell according to safetyvalue
            if (columnNumber == 2) {
                switch (result.safetyValue) {
                    case evaluateResult.NOT_SAFE: {
                        redFormat = wb.createCellStyle();
                        redFormat.setFillForegroundColor(IndexedColors.RED.index);
                        redFormat.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
                        for (int i = 0; i < result.length; i++) {
                            currentRow = (Row) method.invoke(sheet, rowNumber - i);
                            currentCell = currentRow.getCell(columnNumber);
                            currentCell.setCellStyle(redFormat);
                        }
                        break;
                    }
                    case evaluateResult.NOT_SAFE_LOW_SPEED: {
                        orangeFormat = wb.createCellStyle();
                        orangeFormat.setFillForegroundColor(IndexedColors.ORANGE.index);
                        orangeFormat.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
                        for (int i = 0; i < result.length; i++) {
                            currentRow = (Row) method.invoke(sheet, rowNumber - i);
                            currentCell = currentRow.getCell(columnNumber);
                            currentCell.setCellStyle(orangeFormat);
                        }
                        break;
                    }
                }
            }
        }
        if (value instanceof Date) {
            currentCell.setCellValue((Date) value);
            currentCell.setCellStyle(dateFormat);
        }
    }

    private <T> Method createCell(HSSFSheet sheet, int rowNumber, int columnNumber, T value, HSSFWorkbook wb, evaluateResult result) throws InvocationTargetException, IllegalAccessException {
        Method method = getMethod(columnNumber);
        if (result != null) {
            createCell(sheet, rowNumber, columnNumber, value, method, wb, result);
        } else {
            createCell(sheet, rowNumber, columnNumber, value, method, wb, new evaluateResult(0, evaluateResult.SAFE));
        }
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

    //returns a proper file for saving the excel/log
    private String getFileName() {
        if (currentFileName == null) {
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Calendar calendar = Calendar.getInstance();
            int month = calendar.get(Calendar.MONTH) + 1;
            int year = calendar.get(Calendar.YEAR);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int i = 0;
            while (true) {
                String name = "tesi-" + day + "-" + month + "-" + year + "-" + i;
                File returnFileName = new File(folder.getPath(), name + ".txt");
                if (!returnFileName.exists()) {
                    currentFileName = name;
                    return name;
                }
                i++;
            }
        }
        return currentFileName;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            currentSpeed = location.getSpeed();
        }
        if (location.getLatitude() != 0 && location.getLongitude() != 0) {
            currentLatitude = location.getLatitude();
            currentLongitude = location.getLongitude();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /*
    prints an event on the log file
     */
    private void logEvent(int index, Date currentDate, String eventDescription) {
        String outputFileName = getFileName();
        try {
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File outputFile = new File(folder, outputFileName + ".txt");
            FileWriter writer = new FileWriter(outputFile, true);

            //write timestamp and description only if no location
            writer.append(currentDate.toString() + " " + eventDescription);

            //if there's a location, append it
            if (index != NO_LOCATION_EVENT) {
                writer.append(" lat: " + queue.getData(index).latitude + " long: " + queue.getData(index).longitude);
            }
            writer.append("\n");
            writer.flush();
            writer.close();
            MediaScannerConnection.scanFile(this, new String[]{outputFile.getPath()}, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
