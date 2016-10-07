package com.example.ricca.tesi;

import android.content.ActivityNotFoundException;
import android.content.Intent;

import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private final int REQ_CODE_SPEECH_INPUT = 0;
    public final static String INTENT_INT_TAG = "INT", INTENT_STRING_TAG = "STRING";
    public final static int EVENT_TOGGLE = 0, EVENT_INDEX = 1;
    private boolean gathering;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    public void toggleGathering(View view) {
        if (gathering) {
            ((Button) findViewById(R.id.gatherButton)).setText("start data gathering");
            gathering = false;
        } else {
            ((Button) findViewById(R.id.gatherButton)).setText("stop data gathering");
            gathering = true;
        }
        Intent intent = new Intent(this, gathererService.class);
        intent.putExtra(INTENT_INT_TAG, EVENT_TOGGLE);
        startService(intent);
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
    }

    public void promptSpeechInput(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    "not supported",
                    Toast.LENGTH_SHORT).show();
        }
        intent = new Intent(this, gathererService.class);
        intent.putExtra(INTENT_INT_TAG, EVENT_INDEX);
        startService(intent);
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
}

