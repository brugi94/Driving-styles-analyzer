package com.example.ricca.tesi;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.jar.Manifest;

public class MainActivity extends AppCompatActivity {
    private final int REQ_CODE_SPEECH_INPUT = 0;
    public final static String INTENT_INT_TAG = "INT", INTENT_STRING_TAG = "STRING";
    public final static int EVENT_TOGGLE = 0, EVENT_INDEX = 1;
    private boolean gathering;
    private TextToSpeech t1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.ITALY);
                }
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onBroadcast);
    }

    public void toggleGathering(View view) {
        //if we don't have proper permissions, ask them. Otherwise toggle gathering
        if (!checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                !checkPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ||
                !checkPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, 0);
            requestPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, 0);
            requestPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, 0);
        } else {
            Intent intent = new Intent(this, gathererService.class);
            intent.putExtra(INTENT_INT_TAG, EVENT_TOGGLE);
            startService(intent);
        }
    }

    @Override
    protected void onDestroy() {
        Intent intent = new Intent(this, gathererService.class);
        stopService(intent);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver((onBroadcast)
                , new IntentFilter(gathererService.INTENT_TAG));
    }

    public void addFrenata(View view) {
        startService("Frenata");
//
//        if (gathering) {
//            Intent intent = new Intent(this, gathererService.class);
//            intent.putExtra(INTENT_INT_TAG, EVENT_INDEX);
//            startService(intent);
//            intent = new Intent(this, gathererService.class);
//            intent.putExtra(INTENT_STRING_TAG, "Frenata");
//            startService(intent);
//            t1.speak("velocità ricevuta", TextToSpeech.QUEUE_ADD, null, "velocità request");
//
//            /*Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
//            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
//                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
//            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
//            try {
//                startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
//            } catch (ActivityNotFoundException a) {
//                Toast.makeText(getApplicationContext(),
//                        "not supported",
//                        Toast.LENGTH_SHORT).show();
//            }
//            intent = new Intent(this, gathererService.class);
//            intent.putExtra(INTENT_INT_TAG, EVENT_INDEX);
//            startService(intent);*/
//        } else {
//            Toast.makeText(getApplicationContext(), "Start gathering first", Toast.LENGTH_SHORT).show();
//        }
    }

    public void addAccelerazione(View view) {
        startService("Accelerazione");
//        if (gathering) {
//            Intent intent = new Intent(this, gathererService.class);
//            intent.putExtra(INTENT_INT_TAG, EVENT_INDEX);
//            startService(intent);
//            intent = new Intent(this, gathererService.class);
//            intent.putExtra(INTENT_STRING_TAG, "Accelerazione");
//            startService(intent);
//            t1.speak("velocità ricevuta", TextToSpeech.QUEUE_ADD, null, "velocità request");
//
//            /*Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
//            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
//                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
//            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
//            try {
//                startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
//            } catch (ActivityNotFoundException a) {
//                Toast.makeText(getApplicationContext(),
//                        "not supported",
//                        Toast.LENGTH_SHORT).show();
//            }
//            intent = new Intent(this, gathererService.class);
//            intent.putExtra(INTENT_INT_TAG, EVENT_INDEX);
//            startService(intent);*/
//        } else {
//            Toast.makeText(getApplicationContext(), "Start gathering first", Toast.LENGTH_SHORT).show();
//        }
    }

    private void startService(String tag) {
        if (gathering) {
            Intent intent = new Intent(this, gathererService.class);
            intent.putExtra(INTENT_INT_TAG, EVENT_INDEX);
            startService(intent);
            intent = new Intent(this, gathererService.class);
            intent.putExtra(INTENT_STRING_TAG, tag);
            startService(intent);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                t1.speak(tag + "aggiunta", TextToSpeech.QUEUE_ADD, null, "velocità request");
            }
        } else {
            Toast.makeText(getApplicationContext(), "Start gathering first", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    Intent intent = new Intent(this, gathererService.class);
                    intent.putExtra(INTENT_STRING_TAG, result.get(0));
                    startService(intent);
                }
                break;
            }

        }
    }

    private BroadcastReceiver onBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctxt, Intent i) {
            if (gathering) {
                ((Button) findViewById(R.id.gatherButton)).setText("start data gathering");
                gathering = false;
            } else {
                ((Button) findViewById(R.id.gatherButton)).setText("stop data gathering");
                gathering = true;
            }
        }
    };

    /**
     * @param permission the requested permission
     */
    private void requestPermission(String permission, int id) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && (!checkPermission(permission))) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    id);
        }
    }

    /**
     * @param permission the permission to check for
     * @return true if granted, false otherwise
     */
    private boolean checkPermission(String permission) {
        return ((ContextCompat.checkSelfPermission(getApplicationContext(),
                permission)) == PackageManager.PERMISSION_GRANTED);
    }
}

