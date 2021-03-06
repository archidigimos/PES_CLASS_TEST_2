package com.example.archismansarkar.pes_class_test_2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int RECORDER_SAMPLERATE = 8000;

    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;

    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private final Handler mHandler = new Handler();
    private Runnable mTimer;
    private LineGraphSeries<DataPoint> mSeries;
    private double graphLastXValue = 0d;
    public double data = 0d;
    public double decibel = 0d;
    public TextView textView;

    private static final String[] ALL_PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    private static final int RECORD_REQUEST_CODE = 101;
    private static final int STORAGE_REQUEST_CODE = 3;
    private static final int ALL_REQUEST_CODE = 0;
    private int permissionAudio, permissionStorage;
    private AppPermissions mRuntimePermission;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRuntimePermission = new AppPermissions(this);
        if (mRuntimePermission.hasPermission(ALL_PERMISSIONS)) {
            Toast.makeText(this, "All permission already given", Toast.LENGTH_SHORT).show();
        } else {
            mRuntimePermission.requestPermission(this, ALL_PERMISSIONS, ALL_REQUEST_CODE);
        }

        setButtonHandlers();
        enableButtons(false);
        textView = (TextView) findViewById(R.id.textView);

        GraphView graph = (GraphView) findViewById(R.id.graph);
        mSeries = new LineGraphSeries<>();
        graph.addSeries(mSeries);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(100);

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    }

    private void makeRequest() {
        ActivityCompat.requestPermissions(this, ALL_PERMISSIONS, ALL_REQUEST_CODE);
    }

    private void setButtonHandlers() {
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
    }

    private void enableButton(int id, boolean isEnable) {
        ((Button) findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording() {

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

        recorder.startRecording();

        mTimer = new Runnable() {
            @Override
            public void run() {
                graphLastXValue += 1d;
                if(decibel>=-30.0)textView.setText("SNORING");
                else textView.setText("NORMAL");
                mSeries.appendData(new DataPoint(graphLastXValue, decibel), true, 100);
                mHandler.postDelayed(this, 35);
            }
        };
        mHandler.postDelayed(mTimer, 1000);


        isRecording = true;

        recordingThread = new Thread(new Runnable() {

            public void run() {

                writeAudioDataToFile();

            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    //Conversion of short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];

        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }

    private void writeAudioDataToFile() {

        short sData[] = new short[BufferElements2Rec];
        String filePath = "/sdcard/8k16bitMono.pcm";

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            recorder.read(sData, 0, BufferElements2Rec);

            for(int x=0; x<BufferElements2Rec; x++) {
                Log.d("AudioData ", String.valueOf(sData[x]));

                data = sData[x];

                if (data>0)decibel = 20.0*Math.log10(data/65535.0);
                else if (data<0){
                    data = 0d-data;
                    decibel = 20.0*Math.log10(data/65535.0);
                }
            }

            try {
                // writes the data to file from buffer stores the voice buffer
                byte bData[] = short2byte(sData);

                os.write(bData, 0, BufferElements2Rec * BytesPerElement);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;


            recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;

            mHandler.removeCallbacks(mTimer);
        }
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    if (mRuntimePermission.hasPermission(ALL_PERMISSIONS)) {
                        enableButtons(true);
                        startRecording();
                    } else {
                        mRuntimePermission.requestPermission(MainActivity.this, ALL_PERMISSIONS, ALL_REQUEST_CODE);
                    }
                    break;
                }
                case R.id.btnStop: {
                    if (mRuntimePermission.hasPermission(ALL_PERMISSIONS)) {
                        enableButtons(false);
                        stopRecording();
                    } else {
                        mRuntimePermission.requestPermission(MainActivity.this, ALL_PERMISSIONS, ALL_REQUEST_CODE);
                    }
                    break;
                }
            }
        }
    };

    // onClick of backbutton finishes the activity.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case ALL_REQUEST_CODE:
                List<Integer> permissionResults = new ArrayList<>();
                for (int grantResult : grantResults) {
                    permissionResults.add(grantResult);
                }
                if (permissionResults.contains(PackageManager.PERMISSION_DENIED)) {
                    Toast.makeText(this, "All Permissions not granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "All Permissions granted", Toast.LENGTH_SHORT).show();
                }
                break;
            case RECORD_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, "Microphone Permissions not granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Microphone Permissions granted", Toast.LENGTH_SHORT).show();
                }
                break;
            case STORAGE_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, "Storage Permissions not granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Storage Permissions granted", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


}
