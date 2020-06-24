package com.abc1225.audio;

import android.app.Service;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

import androidx.annotation.Nullable;

public class AudioCapture extends Service {
    private static final String TAG = "sloth";

    private static int SERVICE_ID = 123;
    private static String NOTIFICATION_CHANNEL_ID = "AudioCapture channel";

    private static int NUM_SAMPLES_PER_READ = 1024;
    private static int BYTES_PER_SAMPLE = 2; // 2 bytes since we hardcoded the PCM 16-bit format
    private static int BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE;
    public static String ACTION_START = "AudioCapture:Start";
    public static String ACTION_STOP = "AudioCapture:Stop";
    public static String EXTRA_RESULT_DATA = "AudioCapture:Extra:ResultData";

    public static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
    // 输入源 麦克风
    private final static int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    // 采样率 44100Hz，所有设备都支持
    private final static int SAMPLE_RATE = 44100;
    // 通道 单声道，所有设备都支持
    private final static int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    // 精度 16 位，所有设备都支持
    private final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // 通道数 单声道
    private static final int CHANNEL_COUNT = 1;
    // 比特率
    private static final int BIT_RATE = 96_000;

    // 缓冲区字节大小
    private int mBufferSizeInBytes = BUFFER_SIZE_IN_BYTES;
    private MediaCodec mMediaCodec;




    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    private Thread audioCaptureThread;
    private AudioRecord mAudioRecord;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(
                SERVICE_ID,
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build()
        );

        // use applicationContext to avoid memory leak on Android 10.
        // see: https://partnerissuetracker.corp.google.com/issues/139732252
        mediaProjectionManager =
                (MediaProjectionManager)getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        Log.d(TAG, "onCreate  mediaProjectionManager: " +  mediaProjectionManager);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audio Capture Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationManager manager = getApplicationContext().getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + intent.getAction());
         if (intent != null) {
                if(ACTION_START.equals(intent.getAction())) {
                    mediaProjection =
                            mediaProjectionManager.getMediaProjection(
                                    Activity.RESULT_OK,
                                    (Intent)intent.getParcelableExtra(EXTRA_RESULT_DATA)
                        );
                    startAudioCapture();
                    return Service.START_STICKY;
                } else if(ACTION_STOP.equals(intent.getAction())) {
                    stopAudioCapture();
                    return  Service.START_NOT_STICKY;
                }else{
                    throw new IllegalArgumentException("Unexpected action received: ${intent.action}");
                }
        } else {
            return  Service.START_NOT_STICKY;
        }
    }

    private void startAudioCapture() {
        if(mediaProjection == null){
            throw new IllegalArgumentException("Unexpected action mediaProjection is null");
        }
        // TODO provide UI options for inclusion/exclusion
        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        /**
         * Using hardcoded values for the audio format, Mono PCM samples with a sample rate of 8000Hz
         * These can be changed according to your application's needs
         */
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build();


        // 获得缓冲区字节大小
//        mBufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
//        if (mBufferSizeInBytes <= 0) {
//            throw new RuntimeException("AudioRecord is not available, minBufferSize: " + mBufferSizeInBytes);
//        }


        //mAudioRecord = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, mBufferSizeInBytes);


        mAudioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                // For optimal performance, the buffer size
                // can be optionally specified to store audio samples.
                // If the value is not specified,
                // uses a single frame and lets the
                // native code figure out the minimum buffer size.
                .setBufferSizeInBytes(mBufferSizeInBytes)
                .setAudioPlaybackCaptureConfig(config)
                .build();

        if(mAudioRecord != null)
            mAudioRecord.startRecording();
        // 初始化编码信息
        createMediaCodec();
        audioCaptureThread = new Thread(new Runnable() {
            @Override
            public void run() {
                File outputFile = createAudioFile();
                startAsync(outputFile);
            }
        });
        audioCaptureThread.start();

    }

    private File createAudioFile() {
        File audioCapturesDirectory = new File(getExternalFilesDir(null), "/AudioCaptures");
        if (!audioCapturesDirectory.exists()) {
            audioCapturesDirectory.mkdirs();
        }
        String timestamp = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss", Locale.US).format(new Date());
        String fileName = "Capture-"+timestamp+".aac";
        return new File(audioCapturesDirectory.getAbsolutePath() + "/" + fileName);
    }


    private void startAsync(File outFile) {
        try {
            Log.d(TAG, "start() called with: outFile = [" + outFile + "]");
            FileOutputStream fos = new FileOutputStream(outFile);
            mMediaCodec.start();
            mAudioRecord.startRecording();
            byte[] buffer = new byte[mBufferSizeInBytes];
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
            try {
                while (!audioCaptureThread.isInterrupted()) {
                    int readSize = mAudioRecord.read(buffer, 0, mBufferSizeInBytes);
                    if (readSize > 0) {
                        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                        if (inputBufferIndex >= 0) {
                            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            inputBuffer.put(buffer);
                            inputBuffer.limit(buffer.length);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, readSize, 0, 0);
                        }

                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        while (outputBufferIndex >= 0) {
                            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            byte[] chunkAudio = new byte[bufferInfo.size + 7];// 7 is ADTS size
                            addADTStoPacket(chunkAudio, chunkAudio.length);
                            outputBuffer.get(chunkAudio, 7, bufferInfo.size);
                            outputBuffer.position(bufferInfo.offset);
                            fos.write(chunkAudio);
                            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        }
                    } else {
                        Log.w(TAG, "read audio buffer error:" + readSize);
                        break;
                    }
                }
            } finally {
                fos.close();
            }
        }catch (Exception e){e.printStackTrace();}
    }


    private void stopAudioCapture() {
        if(mediaProjection == null){
            throw new IllegalArgumentException("Tried to stop audio capture, but there was no ongoing capture in place!");
        }
        try {
            if(audioCaptureThread != null){
                audioCaptureThread.interrupt();
                audioCaptureThread.join();
            }
        }catch (Exception e){e.printStackTrace();}

        if(null != mMediaCodec){
            mMediaCodec.stop();
            mMediaCodec.release();
        }
        if(mAudioRecord != null){
            mAudioRecord.stop();
            mAudioRecord.release();
        }
        mAudioRecord = null;

        mediaProjection.stop();
        stopSelf();
    }

    public void createMediaCodec() {
        try {
            MediaCodecInfo mediaCodecInfo = selectCodec(MIMETYPE_AUDIO_AAC);
            if (mediaCodecInfo == null) {
                throw new RuntimeException(MIMETYPE_AUDIO_AAC + " encoder is not available");
            }
            Log.i(TAG, "createMediaCodec: mediaCodecInfo " + mediaCodecInfo.getName());

            MediaFormat format = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);

            mMediaCodec = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
            mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }catch (Exception e){e.printStackTrace();}

    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * Add ADTS header at the beginning of each and every AAC packet.
     * This is needed as MediaCodec encoder generates a packet of raw
     * AAC data.
     * <p>
     * Note the packetLen must count in the ADTS header itself.
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 1;  //CPE
        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}
