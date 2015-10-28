package soc.cs5248.player;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Benze on 10/1/15.
 */
public class MediaPlayer {

    private static final String TAG = "MediaPlayer";

    private Surface videoSurface;

    private Context context;

    private TrackRenderer videoRenderer;

    private TrackRenderer audioRenderer;

    private MediaExtractor mediaExtractor;

    private DataSource mediaDataSource;

    private int audioTrackIndex = -1;

    private int videoTrackIndex = -1;

    private final static int TIMEOUT_US = 5000;

    // 3 threads for playing media, one for data extraction, one for video, one for audio
    private ExecutorService mediaPlayBackService = Executors.newFixedThreadPool(3);

    /**
     * The player state
     */
    private enum State {
        IDLE,
        INITIALIZED,
        PREPARED,
        STARTED,
        PAUSED
    }

    private State state = State.IDLE;

    public MediaPlayer(Context context) {
        this.context = context;
    }

    public void setDataSource(DataSource dataSource) {
        mediaDataSource = dataSource;
        state = State.INITIALIZED;
    }

    public void setVideoSurface(Surface surface) {
        this.videoSurface = surface;
    }

    /**
     * prepare media content, call before start playing
     */
    public void prepare() throws IOException {
        if (state != State.INITIALIZED) {
            Log.e(TAG, "prepare while not in INITIALIZED state.");
            return;
        }

        // TODO: set the initialization data
        //FileDescriptor fd = mediaDataSource.getInitializationData();
        String mediaFile = mediaDataSource.getNextMedia();
        if (mediaFile != null) {
            mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(mediaFile);
        } else {
            Log.e(TAG, "no initialization file");
            throw new RuntimeException("No media file to play");
        }

        selectTrack();

        // create video renderer if a video surface has been set
        if (videoSurface != null && videoTrackIndex != -1) {
            videoRenderer = new VideoRenderer(videoSurface,
                    mediaExtractor.getTrackFormat(videoTrackIndex));
        }

        //create audio renderer
        if (audioTrackIndex != -1) {
            audioRenderer = new AudioRenderer(mediaExtractor.getTrackFormat(audioTrackIndex));
        }
        state = State.PREPARED;
    }

    private void selectTrack() {
        // find out video and audio track index
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video")) {
                videoTrackIndex = i;
                mediaExtractor.selectTrack(videoTrackIndex);
            } else if (mime.startsWith("audio")) {
                audioTrackIndex = i;
                mediaExtractor.selectTrack(audioTrackIndex);
            }
        }
    }


    private void nextMediaExtractor() {
        String mediaFile = mediaDataSource.getNextMedia();
        if(mediaFile != null) {
            Log.d(TAG, "new media file: " + mediaFile);
            mediaExtractor = new MediaExtractor();
            try {
                mediaExtractor.setDataSource(mediaFile);
            } catch (IOException e) {
                throw new RuntimeException("Can not open media file:" + mediaFile);
            }
            selectTrack();
        } else {
            mediaExtractor = null;
        }
    }

    public void start() {
        if (state != State.PREPARED) {
            Log.e(TAG, "start playing while not in PREPARED state.");
            return;
        }
        // TODO: start playing the media file
        mediaPlayBackService.execute(new Runnable() {
            @Override
            public void run() {
                // extract data from MediaExtractor
                //FileDescriptor mediaFile = mediaDataSource.getNextMedia();
                //String mediaFile = mediaDataSource.getNextMedia();
                while(mediaExtractor != null) {
                    do {
                        int sampleTrack = mediaExtractor.getSampleTrackIndex();
                        Log.d(TAG, "sample track:" + sampleTrack + ", sampleTime: " + mediaExtractor.getSampleTime());
                        if (videoTrackIndex != -1 && sampleTrack == videoTrackIndex) {
                            ByteBuffer inputBuffer = videoRenderer.getAvailableInputBuffer();
                            if (inputBuffer != null) {
                                int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
                                videoRenderer.queueInputBuffer(sampleSize, mediaExtractor.getSampleTime());
                            }
                        } else if (audioTrackIndex != -1 && sampleTrack == audioTrackIndex) {
                            ByteBuffer inputBuffer = audioRenderer.getAvailableInputBuffer();
                            if (inputBuffer != null) {
                                int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
                                audioRenderer.queueInputBuffer(sampleSize, mediaExtractor.getSampleTime());
                            }
                        } else {
                            Log.e(TAG, "sample from unselected track or no more track:" + sampleTrack);
                        }
                    } while (mediaExtractor.advance());
                    nextMediaExtractor();
//                    mediaFile = mediaDataSource.getNextMedia();
                }
                // end of media data
                if (videoRenderer != null) {
                    videoRenderer.endOfInputData();
                }
                if (audioRenderer != null) {
                    audioRenderer.endOfInputData();
                }
                Log.d(TAG, "end of input data");
            }
        });

        if (videoRenderer != null) {
            mediaPlayBackService.execute(new Runnable() {
                @Override
                public void run() {
                    videoRenderer.render();
                    Log.d(TAG, "video playback finishes");
                    videoRenderer.release();
                }
            });
        }
        if (audioRenderer != null) {
            mediaPlayBackService.execute(new Runnable() {
                @Override
                public void run() {
                    audioRenderer.render();
                    audioRenderer.release();
                    Log.d(TAG, "audio playback finishes");
                }
            });
        }
        state = State.STARTED;
    }


}
