package soc.cs5248.recorder;

import android.media.*;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Benze on 10/24/15.
 */
public class AudioRecorder {

    private static final String TAG = "AudioRecorder";

    private static final int SAMPLERATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder;
    private boolean isRecording = false;

    private int audioBufferSize;

    MediaCodec audioEncoder;

    public AudioRecorder() {
        audioBufferSize = AudioRecord.getMinBufferSize(SAMPLERATE,
                CHANNEL_CONFIG, AUDIO_FORMAT);
        Log.d(TAG, "Min audio buffer size" + audioBufferSize);
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLERATE, CHANNEL_CONFIG,
                AUDIO_FORMAT, audioBufferSize);
        prepareEncoder();
    }

    private void prepareEncoder() {
        MediaFormat format  = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLERATE, 1);
        try {
            audioEncoder = MediaCodec.createByCodecName(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();
        } catch (IOException e) {
            e.printStackTrace();
            //throw new RuntimeException("Initializing audio encoder failed");
        }
    }

    public void record() {
        ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
        short[] audioData = new short[audioBufferSize];
        long startTime = System.nanoTime();
        while (true) {
            // gets the voice output from microphone to byte format
            int dataLen = recorder.read(audioData, 0, audioBufferSize);
            int index = audioEncoder.dequeueInputBuffer(50);
            if(index >= 0) {
                ByteBuffer inputBuffer = inputBuffers[index];
                for(int i = 0; i < dataLen; i++) {
                    inputBuffer.putShort(audioData[i]);
                }
                long timeStampUS = (System.nanoTime() - startTime) / 1000;
                audioEncoder.queueInputBuffer(index, 0, dataLen, timeStampUS, 0);
            } else {
                Log.d(TAG, "can't get input buffer: " + index);
            }
        }
    }
}
