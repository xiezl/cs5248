package soc.cs5248.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Benze on 10/9/15.
 */
public class AudioRenderer extends TrackRenderer {

    private final static String TAG = "AudioRenderer";

    private MediaFormat mediaFormat;

    private AudioTrack audioTrack;

    private int sampleRate;

    private int channelCount;

    private int currentInputBufferIndex = -1;

    private final static long DEQUEUE_BUFFER_TIMEOUT_US = 5000;

    public AudioRenderer(MediaFormat mediaFormat) {
        this.mediaFormat = mediaFormat;
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        // initialize AudioTrack
        int channelConfiguration = channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minSize = AudioTrack.getMinBufferSize(sampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfiguration,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
        try {
            mediaCodec = MediaCodec.createDecoderByType(mime);
            mediaCodec.configure(mediaFormat, null, null, 0);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playAudio(ByteBuffer byteBuffer, int size) {
        // TODO: assuming there is only one channel, how to deal with multiple?
        Log.d(TAG, "play audio size: " + size);
        final byte[] chunk = new byte[size];
        byteBuffer.get(chunk);
        if(chunk.length > 0){
            audioTrack.write(chunk, 0, chunk.length);
        }
    }

    // synchronize time before output video
    private void playAudioWithSynchronization(ByteBuffer outputBuffer, int outputSize,
                                              long playbackTimeMS, long presentationTimeUs) {
        // a very simple mechanism to get the frame displayed at a proper time
        long timeOffSet = playbackTimeMS - presentationTimeUs / 1000;
        // TODO: tune the time offset for audio synchronization
        if(timeOffSet > 30) {
            // it has been 30 ms later than the time when this frame should be played, drop it
            Log.d(TAG, "frame occur to late, drop frame");
        } else {
            if(-timeOffSet > 10) {
                Log.d(TAG, "time need to wait: " + (-timeOffSet));
                long waitTime = ((-timeOffSet) / 10) * 10;
                // it's earlier than the time when this frame can be played, wait for a certain period
                SystemClock.sleep(waitTime);
            }
            playAudio(outputBuffer, outputSize);
        }
    }

    @Override
    public void render() {
        audioTrack.play();
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
        while(true) {
            int outputIndex = mediaCodec.dequeueOutputBuffer(outputInfo, DEQUEUE_BUFFER_TIMEOUT_US);
            if(outputIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputIndex];
                Log.d(TAG, "got frame, size " + outputInfo.size + "/" + outputInfo.presentationTimeUs);
                if(playbackReferenceTimeMS < 0) {
                    playbackReferenceTimeMS = SystemClock.elapsedRealtime();
                    playAudio(outputBuffer, outputInfo.size);
                } else {
                    // TODO: need to be synchronized with the presentation time of the current frame after pausing
                    long playbackTimeMS = SystemClock.elapsedRealtime() - playbackReferenceTimeMS;
                   // playAudio(outputBuffer, outputInfo.size);
                    playAudioWithSynchronization(outputBuffer, outputInfo.size,
                            playbackTimeMS, outputInfo.presentationTimeUs);
                }
                mediaCodec.releaseOutputBuffer(outputIndex, false);
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mediaCodec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = mediaCodec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + oformat);
            } else {
                Log.d(TAG, "dequeueOutputBuffer returned " + outputIndex);
            }
            if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "saw output EOS.");
                break;
            }
        }
        audioTrack.release();
    }
}
