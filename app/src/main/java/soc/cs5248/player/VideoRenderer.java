package soc.cs5248.player;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

/**
 * Created by Benze on 10/9/15.
 */
public class VideoRenderer extends TrackRenderer{
    private final static String TAG = "VideoRender";

    private Surface surface;

    private boolean isFirstFrame = true;

    private final static int VIDEO_DEQUEUE_BUFFER_TIMEOUT_US = 500000; //500ms

    public VideoRenderer(Surface surface, MediaFormat mediaFormat) {
        this.surface = surface;
        this.mediaFormat = mediaFormat;
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        try {
            mediaCodec = MediaCodec.createDecoderByType(mime);
            mediaCodec.configure(mediaFormat, surface, null, 0);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void render() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while(true) {
            int outputIndex = mediaCodec.dequeueOutputBuffer(info, VIDEO_DEQUEUE_BUFFER_TIMEOUT_US);
            if (outputIndex >= 0) {
                Log.d(TAG, "video got frame, size " + info.size + "/" + info.presentationTimeUs);
                //if(info.flags && MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                if(info.presentationTimeUs == 0) {
                    // playing the first frame, record the start time
                    playbackReferenceTimeMS = SystemClock.elapsedRealtime();
                    mediaCodec.releaseOutputBuffer(outputIndex, true);
                } else {
                    // TODO: need to be synchronized with the presentation time of the current frame after pausing
                    long playbackTimeMS = SystemClock.elapsedRealtime() - playbackReferenceTimeMS;
                    playVideoWithSynchronization(outputIndex, playbackTimeMS, info.presentationTimeUs);
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "output buffers have changed.");

            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = mediaCodec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + oformat);
            } else {
                Log.d(TAG, "dequeueOutputBuffer returned " + outputIndex);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "output data has ended");
                break;
            }
        }
    }

    @Override
    public void release() {
        mediaCodec.release();

    }

    // synchronize time before output video
    private void playVideoWithSynchronization(int bufferIndex, long playbackTimeMS, long presentationTimeUs) {
        // a very simple mechanism to get the frame displayed at a proper time
        long timeOffSet = playbackTimeMS - presentationTimeUs / 1000;
        if(timeOffSet > 30) {
            // it has been 30 ms later than the time when this frame should be played, drop it
            mediaCodec.releaseOutputBuffer(bufferIndex, false);
        } else {
            if(-timeOffSet > 10) {
                Log.d(TAG, "time need to wait: " + (-timeOffSet));
                long waitTime = ((-timeOffSet) / 10) * 10;
                // it's earlier than the time when this frame can be played, wait for a certain period
                SystemClock.sleep(waitTime);
            }
            mediaCodec.releaseOutputBuffer(bufferIndex, true);
        }
    }
}
