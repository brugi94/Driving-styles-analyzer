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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by ricca on 03/10/2016.
 */
public class gathererService extends Service implements SensorEventListener, LocationListener {
    LocalBroadcastManager broadcaster;
    private SensorManager manager;
    private Sensor accelerometerNoGrav, accelerometerWithGrav;
    private DataQueue noGravQueue, gravQueue;
    private Handler backgroundAccelerometerHandler;
    public int GRAV_SPEED_COLUMN, GRAV_TIMESTAMP_COLUMN, NO_GRAV_TIMESTAMP_COLUMN, HUNDREDTH_OF_SECOND = (int) 10E3, EXPLANATION_COLUMN, CALCULATED_SPEED_COLUMN, LATITUDE_COLUMN, LONGITUDE_COLUMN, POWER_COLUMN, NO_LOCATION_EVENT = -1, NO_GRAV_SPEED_COLUMN, CURRENT_AXIS = 1;
    private HandlerThread backgroundAccelerometerThread;
    private boolean isGathering, isSaving, speedReceived, isGravityEstimated, eventFound;
    public final static String INTENT_TAG = "tag", INTENT_MSG = "msg";
    private int noGravRowNumber = 2, gravRowNumber = 2;
    private final String[] DATA_FIELD_NAMES = {"FILTERED DATA"}, SECOND_ROW_STRINGS = {"x-axis", "y-axis", "z-axis", "timestamp", "speed", "x-axis", "y-axis", "z-axis", "timestamp", " GPS speed", "calculated speed", "powerDelta", "latitude", "longitude"};
    private ArrayList<Integer> RecordedIndexes;
    private ArrayList<String> RecordedEvents;
    private LocationManager locationManager;
    private float currentSpeed, calculatedSpeed, oldCalculatedSpeed;
    private double currentLatitude, currentLongitude;
    private dataEvaluator evaluator;
    private HSSFWorkbook wb;
    private HSSFSheet sheet;
    private MediaPlayer player;
    private String currentFileName;
    private File outputFolder;
    private TextToSpeech t1;
    private long currentGravTime;
    private Timer timer;
    private final int SESSION_DURATION = 600000;
    private long lastGPSSpeedTime;
    private double[] gravity;

    @Override
    public void onCreate() {
        super.onCreate();
        NO_GRAV_TIMESTAMP_COLUMN = 3;
        NO_GRAV_SPEED_COLUMN = NO_GRAV_TIMESTAMP_COLUMN + 1;
        GRAV_TIMESTAMP_COLUMN = NO_GRAV_SPEED_COLUMN + 4;
        GRAV_SPEED_COLUMN = GRAV_TIMESTAMP_COLUMN + 1;
        CALCULATED_SPEED_COLUMN = GRAV_SPEED_COLUMN + 1;
        POWER_COLUMN = CALCULATED_SPEED_COLUMN + 1;
        LATITUDE_COLUMN = POWER_COLUMN + 1;
        LONGITUDE_COLUMN = LATITUDE_COLUMN + 1;
        EXPLANATION_COLUMN = LONGITUDE_COLUMN + 1;
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
                                backgroundAccelerometerHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        saveExcel(true);
                                    }
                                });
                                System.gc();
                            } else {
                                startGathering();
                            }

                            break;
                        }
                    }

                    case MainActivity.EVENT_INDEX: {
                        if (isGathering) {
                            RecordedIndexes.add(noGravQueue.getLength());
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
        } else {
            Toast.makeText(getApplicationContext(), "saving excel, please wait", Toast.LENGTH_LONG).show();
        }
        return START_STICKY;
    }

    /**
     * Initializes needed variables, registers listeners for GPS and accelerometer
     * Also creates the output folder, the timer for saving excel and logs the start event
     */
    private void startGathering() {
        initialize();
        /*register accelerometer listener arguments:
        1) desired listener
        2) desired sensor
        3) rate at which the events are delivered in microseconds
        4) handler where the callback is executed
        */
        manager.registerListener(this, accelerometerNoGrav, 5 * HUNDREDTH_OF_SECOND, backgroundAccelerometerHandler);
        manager.registerListener(this, accelerometerWithGrav, HUNDREDTH_OF_SECOND, backgroundAccelerometerHandler);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "no permissions", Toast.LENGTH_SHORT).show();
            return;
        }

        /*register GPS listener arguments:
            1) which type of GPS
            2) minimum interval between samples
            3) minimum distance between samples
            4) which listener to register
        */
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        isGathering = true;
        sendBroadcast();
    }

    /*
    sets up the fields
     */
    private void initialize() {
        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.ITALY);
                }
            }
        });
        timer = new Timer();
        if (evaluator == null) {
            evaluator = new dataEvaluator();
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
        gravity = new double[3];
        noGravQueue = new DataQueue(DataQueue.BUTTERWORTH, 0.1);
        gravQueue = new DataQueue(DataQueue.BUTTERWORTH, 0.1);
        RecordedIndexes = new ArrayList<Integer>();
        RecordedEvents = new ArrayList<String>();
        broadcaster = LocalBroadcastManager.getInstance(this);
        noGravRowNumber = 2;
        gravRowNumber = 2;
        speedReceived = false;
        currentSpeed = 0;
        currentGravTime = 0;
        eventFound = false;
        //create the output folder
        outputFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), getFileName());
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                backgroundAccelerometerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        saveExcel(false);
                    }
                });
            }
        }, SESSION_DURATION, SESSION_DURATION);
        logEvent(NO_LOCATION_EVENT, new Date(), "gathering started");
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

//        //setup dateFormat style
//        dateFormat = wb.createCellStyle();
//        HSSFDataFormat df = wb.createDataFormat();
//        dateFormat.setDataFormat(df.getFormat("[h]:mm:ss.000;@"));

        //print the strings on the second row
        Row currentRow = sheet.createRow(1);
        for (int i = 0; i < SECOND_ROW_STRINGS.length; i++) {
            Cell currentCell = currentRow.createCell(i);
            currentCell.setCellValue(SECOND_ROW_STRINGS[i]);
        }

        Cell currentCell = currentRow.createCell(EXPLANATION_COLUMN);
        currentCell.setCellValue("power Delta è la differenza di energia cinetica dall'inizio della frenata (accelerazione <0) fino all'istante attuale, diviso la durata della frenata");
        currentRow = sheet.createRow(2);
        currentCell = currentRow.createCell(EXPLANATION_COLUMN);
        currentCell.setCellValue("Gli eventi che hanno un tipo sono quelli registrati a voce, quelli che non lo hanno individuati dall'app");
    }


    private void retrieveSensor() {
        if (locationManager == null) {
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(getApplicationContext(), "gps not allowed", Toast.LENGTH_LONG).show();
            }
        }
        if (manager == null) {
            manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if ((accelerometerNoGrav = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)) == null) {
                Toast.makeText(getApplicationContext(), "linear acceleration not supported", Toast.LENGTH_SHORT).show();
                stopSelf();
            }
            if ((accelerometerWithGrav = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)) == null) {
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
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            noGravQueue.add(new Data(event), currentSpeed, currentLatitude, currentLongitude);
            if (speedReceived) {
                writeNoGravData(noGravQueue.getData(noGravQueue.getLength() - 1));
            }
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (currentGravTime == 0) {
                currentGravTime = System.currentTimeMillis();
            }
            long time = System.currentTimeMillis();
            double timeDelta = ((double) (time - currentGravTime)) / 1000;
            if (!speedReceived && !isGravityEstimated) {
                for (int i = 0; i < 3; i++) {
                    gravity[i] = event.values[i] * 0.1 + gravity[i] * 0.9;
                }
            }
            if (timeDelta >= 0.04) {
                if (speedReceived) {
                    for (int i = 0; i < 3; i++) {
                        event.values[i] -= gravity[i];
                    }
                    gravQueue.add(new Data(event), currentSpeed, currentLatitude, currentLongitude);
                    //print timestamp
                    createCell(sheet, gravRowNumber, GRAV_TIMESTAMP_COLUMN, new Date());
                    analyzeAndUseGravData(gravQueue.getData(gravQueue.getLength() - 1), timeDelta);
                }
                currentGravTime = System.currentTimeMillis();
            }
        }
    }

    /*
    evaluates the sample's safety, prints the sample on excel and depending on the safety of the sample will take some actions:
    1)if it's risky an alarm will be started, the event will be logged
    3)if it's safe, the sample is printed on the excel
     */
    private void writeNoGravData(Data sample) {
        //write timestamp
        if (noGravRowNumber > 1) {
            createCell(sheet, noGravRowNumber, NO_GRAV_TIMESTAMP_COLUMN, new Date());
        }
        //print values
        for (int i = 0; i < 3; i++) {
            createCell(sheet, noGravRowNumber, i, sample.accelerations[i]);
        }
        //print speed
        createCell(sheet, noGravRowNumber, NO_GRAV_SPEED_COLUMN, currentSpeed);
        noGravRowNumber++;
    }


    private void analyzeAndUseGravData(Data sample, double timeDelta) {
        //print values
        for (int i = 0; i < 3; i++) {
            createCell(sheet, gravRowNumber, NO_GRAV_SPEED_COLUMN + 1 + i, sample.accelerations[i]);
        }

        //print  GPS speed
        createCell(sheet, gravRowNumber, GRAV_SPEED_COLUMN, sample.speed);

        //update calculated speed
        createCell(sheet, gravRowNumber, CALCULATED_SPEED_COLUMN, calculatedSpeed);
        calculatedSpeed += sample.accelerations[CURRENT_AXIS] * timeDelta;
        calculatedSpeed = (calculatedSpeed < 0) ? 0 : calculatedSpeed;
//            //evaluate the acquired sample
//            evaluateResult result = evaluator.evaluate(sample.accelerations[CURRENT_AXIS], sample.speed, timeDelta);
//            //if it's a different event, it's not logged
//            if (result.powerDelta * oldPowerDelta < 0) {
//                isEventLogged = false;
//            }
//            //if it's dangerous and not logged
//            if (result.safetyValue == evaluateResult.NOT_SAFE && !isEventLogged) {
//                isEventLogged = true;
//
//                createCell(sheet, gravRowNumber - 1, LONGITUDE_COLUMN, currentLongitude);
//                createCell(sheet, gravRowNumber - 1, LATITUDE_COLUMN, currentLatitude);
//
//                //log the event
//                logEvent(noGravQueue.getLength() - 1, new Date(), (result.powerDelta < 0) ? SUDDEN_BRAKE : SUDDEN_ACCELERATION);
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    t1.speak((result.powerDelta < 0) ? "Frenata brusca" : "Accelerazione brusca", TextToSpeech.QUEUE_ADD, null, "velocità request");
//                }
//            }
//
//            //print powerDelta
//            if (gravRowNumber > 2) {
//                createCell(sheet, gravRowNumber - 1, CALCULATED_SPEED_COLUMN, result.powerDelta);
//            }
//            //update old event value
//            oldPowerDelta = result.safetyValue;
        gravRowNumber++;
    }


    /*
    stops receiving updates and logs the event "gathering stopped"
     */

    private void stopGathering() {
        if (isGathering) {
            manager.unregisterListener(this);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            locationManager.removeUpdates(this);
            logEvent(NO_LOCATION_EVENT, new Date(), "gathering stopped");
            isGathering = false;
            isGravityEstimated = true;
            sendBroadcast();
            timer.cancel();
        }
    }

    private void sendBroadcast() {
        Intent intent = new Intent(INTENT_TAG);
        intent.putExtra(INTENT_TAG, INTENT_MSG);
        broadcaster.sendBroadcast(intent);
    }

    /*
    saves the excel document in the default download directory
     */
    private void saveExcel(boolean startNewFile) {
        isSaving = true;
        Log.i("tag", "started saving");

        printEvents();

        Log.i("tag", "write");

        //save the excel
        File outputFile = null;
        try {
            outputFile = new File(outputFolder, "values.xls");
            FileOutputStream outputStream = new FileOutputStream(outputFile, false);
            wb.write(outputStream);
            outputStream.close();
            wb.close();
            MediaScannerConnection.scanFile(this, new String[]{outputFile.getPath()}, null, null);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (startNewFile) {
            noGravQueue = null;
            currentFileName = null;
        }
        Log.i("tag", "done saving");
        isSaving = false;

    }

    /*
    prints the recorded events on the excel sheet
     */
    private void printEvents() {
        //find length of events array
        int minLength = Math.min(RecordedIndexes.size(), RecordedEvents.size());
        //for each event registered
        for (int rowIndex = 0; rowIndex < minLength; rowIndex++) {
            //find the row with its index
            Row currentRow = this.sheet.getRow(RecordedIndexes.get(rowIndex) + 2);
            if (currentRow == null) {
                currentRow = this.sheet.createRow(RecordedIndexes.get(rowIndex) + 2);
            }
            Cell currentCell = currentRow.createCell(POWER_COLUMN);
            //print type of event
            currentCell.setCellValue(RecordedEvents.get(rowIndex));

            //print latitude
            currentCell = currentRow.createCell(LATITUDE_COLUMN);
            int j = RecordedIndexes.get(rowIndex);
            Data datas = noGravQueue.getData(j);
            currentCell.setCellValue(datas.latitude);
            //print longitude
            currentCell = currentRow.createCell(LONGITUDE_COLUMN);
            currentCell.setCellValue(noGravQueue.getData(RecordedIndexes.get(rowIndex)).longitude);
        }
    }

    private void closeBackgroundThread() {
        if (!backgroundAccelerometerThread.quitSafely()) {
            try {
                backgroundAccelerometerThread.join();
                backgroundAccelerometerThread = null;
                backgroundAccelerometerHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        backgroundAccelerometerHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isGathering)
                    saveExcel(true);
            }
        });
        closeBackgroundThread();
        stopGathering();
        player.release();
    }

    /*
    inserts a value in the selected cell
     */
    private <T> void createCell(HSSFSheet sheet, int rowNumber, int columnNumber, T value) {
        Row currentRow = null;
        if (sheet.getRow(rowNumber) == null) {
            currentRow = sheet.createRow(rowNumber);
        } else {
            currentRow = sheet.getRow(rowNumber);
        }
        Cell currentCell = currentRow.createCell(columnNumber);
        if (value instanceof String) {
            currentCell.setCellValue((String) value);
        }
        if (value instanceof Double) {
            currentCell.setCellValue((Double) value);
        }
        if (value instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            currentCell.setCellValue(sdf.format(value));
        }
        if (value instanceof Float) {
            currentCell.setCellValue((Float) value);
        }
    }


    //returns a proper file for saving the excel/log
    private String getFileName() {
        if (currentFileName == null) {
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Calendar calendar = Calendar.getInstance();
            int month = calendar.get(Calendar.MONTH) + 1;
            int year = calendar.get(Calendar.YEAR);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int minute = calendar.get(Calendar.MINUTE);
            int hours = calendar.get(Calendar.HOUR_OF_DAY);
            int i = 0;
            while (true) {
                String name = "tesi-" + day + "-" + month + "-" + year + "-" + hours + ":" + ((minute < 10) ? "0" + minute : minute) + "-" + i;
                File returnFileName = new File(folder.getPath(), name);
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

            if (!speedReceived) {
                //say that we received speed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !speedReceived) {
                    t1.speak("velocità ricevuta", TextToSpeech.QUEUE_ADD, null, "velocità request");
                    speedReceived = true;
                }
                //update speed
                currentSpeed = location.getSpeed();
                calculatedSpeed = currentSpeed;
                oldCalculatedSpeed = currentSpeed;
                lastGPSSpeedTime = System.currentTimeMillis();
            } else {
                //calculate time delta
                long timeDelta = System.currentTimeMillis() - lastGPSSpeedTime;
                lastGPSSpeedTime = System.currentTimeMillis();

                //calculate new speed
                float newSpeed = (float) (calculatedSpeed * 0.5 + location.getSpeed() * 0.5);

                //calculate power
                double power = (Math.pow(newSpeed, 2) - Math.pow(oldCalculatedSpeed, 2)) / timeDelta * 1000;
                createCell(sheet, gravRowNumber, POWER_COLUMN, power);
                if ((power > 60 || power <= -70) && !eventFound) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        t1.speak((power > 0) ? "accelerazione" : "frenata", TextToSpeech.QUEUE_ADD, null, "velocità request");
                    }
                    eventFound = true;
                    new Timer().schedule(new TimerTask() {

                        @Override
                        public void run() {
                            eventFound = false;
                        }
                    }, 5000);
                }

                //update speed values
                currentSpeed = location.getSpeed();
                oldCalculatedSpeed = newSpeed;
                calculatedSpeed = newSpeed;
            }
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
        try {
            File outputFile = new File(outputFolder, "log.txt");
            FileWriter writer = null;
            boolean dw;
            if (!outputFile.exists()) {
                if (!outputFile.createNewFile()) {
                    Toast.makeText(getApplicationContext(), "cant create", Toast.LENGTH_SHORT);
                }
            }
            writer = new FileWriter(outputFile, true);

            //write timestamp and description only if no location
            writer.append(currentDate.toString() + " " + eventDescription);

            //if there's a location, append it
            if (index != NO_LOCATION_EVENT) {
                writer.append(" lat: " + noGravQueue.getData(index).latitude + " long: " + noGravQueue.getData(index).longitude);
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
