package com.abc1225.audio;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class RecorderActivity extends AppCompatActivity {


    private static int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 42;
    private static int  MEDIA_PROJECTION_REQUEST_CODE = 13;


    private MediaProjectionManager mediaProjectionManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ((Button)findViewById(R.id.btn_start_recording)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startCapturing();
            }
        });

        ((Button)findViewById(R.id.btn_stop_recording)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopCapturing();
            }
        });
    }

    private void setButtonsEnabled(boolean isCapturingAudio) {
        ((Button)findViewById(R.id.btn_start_recording)).setEnabled(!isCapturingAudio);
        ((Button)findViewById(R.id.btn_stop_recording)).setEnabled(isCapturingAudio);
    }

    private void startCapturing() {
        if (!isRecordAudioPermissionGranted()) {
            requestRecordAudioPermission();
        } else {
            startMediaProjectionRequest();
        }
    }

    private void stopCapturing() {
        setButtonsEnabled(false);
        startService(new Intent(this, AudioCapture.class).setAction(AudioCapture.ACTION_STOP));
    }

    private boolean isRecordAudioPermissionGranted() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                RECORD_AUDIO_PERMISSION_REQUEST_CODE
        );
    }

    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "Permissions to capture audio granted. Click the button once again.",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        this, "Permissions to capture audio denied.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    /**
     * Before a capture session can be started, the capturing app must
     * call MediaProjectionManager.createScreenCaptureIntent().
     * This will display a dialog to the user, who must tap "Start now" in order for a
     * capturing session to be started. This will allow both video and audio to be captured.
     */
    private void startMediaProjectionRequest() {
        // use applicationContext to avoid memory leak on Android 10.
        // see: https://partnerissuetracker.corp.google.com/issues/139732252
        mediaProjectionManager =
                (MediaProjectionManager)getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                MEDIA_PROJECTION_REQUEST_CODE
        );
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(
                        this,
                        "MediaProjection permission obtained. Foreground service will be started to capture audio.",
                        Toast.LENGTH_SHORT
                ).show();

                Intent audioCaptureIntent = new Intent(this, AudioCapture.class).setAction(AudioCapture.ACTION_START)
                .putExtra(AudioCapture.EXTRA_RESULT_DATA, data);
                startForegroundService(audioCaptureIntent);
                setButtonsEnabled(true);
            } else {
                Toast.makeText(
                        this, "Request to obtain MediaProjection denied.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }



}
