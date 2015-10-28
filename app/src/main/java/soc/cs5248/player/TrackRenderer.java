package soc.cs5248.player;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * Created by Benze on 10/14/15.
 */
public abstract class TrackRenderer {

    // TODO: find out the meaning of this value and what it should be
    protected final static int DEQUEUE_BUFFER_TIMEOUT_US = 5000;

    protected int currentInputBufferIndex = -1;

    protected MediaFormat mediaFormat;

    protected MediaCodec mediaCodec;

    // the time when the video starts playing, serving as the reference point
    protected long playbackReferenceTimeMS = -1;

    // possibly block for a certain time
    public ByteBuffer getAvailableInputBuffer() {
        currentInputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if(currentInputBufferIndex != -1) {
            return mediaCodec.getInputBuffers()[currentInputBufferIndex];
        } else return null;
    }

    public void queueInputBuffer(int sampleSize, long presentationTimeUS) {
        if(currentInputBufferIndex >= 0) {
            mediaCodec.queueInputBuffer(currentInputBufferIndex, 0, sampleSize, presentationTimeUS, 0);
        }
    }

    /** call when the end of data stream is hit */
    public void endOfInputData() {
        int index = mediaCodec.dequeueInputBuffer(-1);
        mediaCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    }

    /** start rendering media*/
    public abstract void render();

    /** release resource*/
    public void release() {
        mediaCodec.release();
    }
}
