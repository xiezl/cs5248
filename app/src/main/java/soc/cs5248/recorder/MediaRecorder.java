package soc.cs5248.recorder;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import soc.cs5248.recorder.libstreaming.SurfaceView;

/**
 * Created by Benze on 10/24/15.
 */
public class MediaRecorder {

    private final static String TAG = "MediaRecorder";
    private final static boolean VERBOSE = true;

    // video format
    private final static int VIDEO_WIDTH = 1280;
    private final static int VIDEO_HEIGHT = 720;
    private final static int BIT_RATE = 3000000;
    private final static int FRAME_RATE = 15;
    private final static int IFRAME_INTERVAL = 5;
    private final static int segmentDurationUS = 3000000;       // 3s for each segment
    private final static String MIME_TYPE_VIDEO = MediaFormat.MIMETYPE_VIDEO_AVC;

    // audio format
    private static final int AUDIO_SAMPLERATE = 44100;
    private static final int AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private final static String MIME_TYPE_AUDIO = MediaFormat.MIMETYPE_AUDIO_AAC;


    private Context context;

    private Camera camera;

    // SurfaceView for camera preview
    private SurfaceView surfaceView;

    private MediaCodec videoEncoder;

    private Surface recordingSurface;

    private MediaCodec audioEncoder;

    private MediaMuxer mediaMuxer;

    private int mediaSegmentIndex;

    private File outputDirectory;

    private ExecutorService executorService = Executors.newFixedThreadPool(3);

    private int videoTrackIndex;

    private boolean readyForMuxing;

    private boolean isRecording = false;

    public MediaRecorder(Context context, SurfaceView surfaceView) {
        this.context = context;
        this.surfaceView = surfaceView;
        prepareVideoEncoder();
        prepareAudioEncoder();
        this.surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            /************** SurfaceHolder Callbacks **************/
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                // surface created, prepare camera and start preview
                prepareCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
                // change is not allowed
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                // TODO: what to do ?
            }
        });
    }

    public boolean isRecording() {
        return isRecording;
    }

    private static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
        // We should make sure that the requested MPEG size is less than the preferred
        // size, and has the same aspect ratio.
        Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            Log.d(TAG, "Camera preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
        }

        for (Camera.Size size : parms.getSupportedPreviewSizes()) {
            if (size.width == width && size.height == height) {
                parms.setPreviewSize(width, height);
                return;
            }
        }

        Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
        if (ppsfv != null) {
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
        }
    }

    private void newOutputDirectory() {

        File mediaStorageDir = new File(context.getFilesDir(),
                "videos");
        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
//                Log.d(TAG, "failed to create directory");
                throw new RuntimeException("failed to create output directory");
            }
        }
        // Create new folder for the current video
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        outputDirectory = new File(mediaStorageDir, timeStamp);
        if(!outputDirectory.exists()) {
            outputDirectory.mkdir();
        }
    }

    /** Create a File for saving video */
    private File getOutputMediaFile(){
        // TODO: change to internal storage
        if(outputDirectory == null) {
            throw new RuntimeException("haven't set up output folder");
        }
        File videoFile = new File(outputDirectory, mediaSegmentIndex + ".mp4");
        mediaSegmentIndex++;
        return videoFile;
    }

    private void newMediaMuxer(MediaFormat videoFormat) {
        if(videoFormat == null)
            return;

        try {
            if(mediaMuxer != null) {
                mediaMuxer.stop();
            }
            File outputFile = getOutputMediaFile();
            Log.d(TAG, "new media output: " + outputFile.getAbsolutePath());
            mediaMuxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoTrackIndex = mediaMuxer.addTrack(videoFormat);
            mediaMuxer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class AudioRecordingTask implements Runnable {

        int audioBufferSize;

        AudioRecord recorder;

        public AudioRecordingTask() {
            audioBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLERATE,
                    AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT);
            Log.d(TAG, "Min audio buffer size: " + audioBufferSize);
            recorder = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC,
                    AUDIO_SAMPLERATE, AUDIO_CHANNEL_CONFIG,
                    AUDIO_FORMAT, audioBufferSize);
        }

        private byte[] short2byte(short[] sData, int len) {
            byte[] bytes = new byte[len * 2];
            for (int i = 0; i < len; i++) {
                bytes[i * 2] = (byte) (sData[i] & 0x00FF);
                bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
                sData[i] = 0;
            }
            return bytes;

        }

        @Override
        public void run() {
            if(audioEncoder == null) {
                throw new RuntimeException("audio encoder is not initialized");
            }
            recorder.startRecording();
            ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
            byte[] audioData = new byte[audioBufferSize];
            long startTime = System.nanoTime();
            while (true) {
                // gets the voice output from microphone to byte format
                int dataLen = recorder.read(audioData, 0, audioBufferSize);
                if(dataLen > 0) {
                    int index = audioEncoder.dequeueInputBuffer(50);
                    if(index >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[index];
                        Log.d(TAG, "get audio data: " + dataLen * 2 + " , buffer size: " + inputBuffer.capacity());
                        inputBuffer.clear();
                        inputBuffer.put(audioData, 0, dataLen);
//                        for(int i = 0; i < dataLen; i++) {
//                            inputBuffer.putShort(audioData[i]);
//                        }
                        long timeStampUS = (System.nanoTime() - startTime) / 1000;
                        audioEncoder.queueInputBuffer(index, 0, dataLen , timeStampUS, 0);
                        Log.d(TAG, "get audio data: " + dataLen);
                    } else {
                        Log.d(TAG, "can't get input buffer: " + index);
                    }
                } else if(dataLen == AudioRecord.ERROR_BAD_VALUE) {
                    Log.d(TAG, "Bad value");
                } else if(dataLen == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "invalid operation");
                }


            }
        }
    }

    private class AudioMuxingTask implements Runnable {

        private final int AUDIO_TIMEOUT_US = 100000;    // 100ms

        @Override
        public void run() {
            ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            MediaFormat audioFormat = null;
            long lastSegmentPointUS = 0;
            while (true) {
                int index = audioEncoder.dequeueOutputBuffer(bufferInfo, -1);
                if (index >= 0) {
                    ByteBuffer encodedData = outputBuffers[index];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + index +
                                " was null");
                    }

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        bufferInfo.size = 0;
                    }

                    if (bufferInfo.size != 0) {
                        if (!readyForMuxing) {
                            throw new RuntimeException("muxer hasn't started");
                        }

                        Log.d(TAG, "presentation time: " + bufferInfo.presentationTimeUs);
                        // recorded 3 seconds, get a new mediamuxer, need to wait fot a key frame
                        if((bufferInfo.presentationTimeUs - lastSegmentPointUS > segmentDurationUS) &&
                                (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                            newMediaMuxer(audioFormat);
                            lastSegmentPointUS = bufferInfo.presentationTimeUs;
                        }
                        // write video sample to muxer
                        mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
                        if (VERBOSE) Log.d(TAG, "sent " + bufferInfo.size + " bytes to muxer");
                    }
                    audioEncoder.releaseOutputBuffer(index, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;      // out of while
                    }
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    // TODO: should not be necessary, try to remove these
                    // should happen before receiving buffers, and should only happen once
                    if (readyForMuxing) {
                        throw new RuntimeException("format changed twice");
                    }
                    audioFormat = audioEncoder.getOutputFormat();
                    newMediaMuxer(audioFormat);
                    Log.d(TAG, "encoder output format changed: " + audioFormat);
                    //prepareMediaMuxer(format);
                    readyForMuxing = true;
                    // now the format is known, start the muxer
//                    videoTrackIndex = mediaMuxer.addTrack(videoFormat);
//                    mediaMuxer.start();
                } else if(index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = videoEncoder.getOutputBuffers();
                } else {
                    Log.w(TAG, "encoder.dequeueOutputBuffer: " +
                            index);
                }
            }
            // TODO: stop encoder and muxer,
            audioEncoder.stop();
            mediaMuxer.stop();
        }
    }

    private class MediaMuxingTask implements Runnable {
        private final int VIDEO_TIMEOUT_US = 500000;      // 500ms

        @Override
        public void run() {
            ByteBuffer[] outputBuffers = videoEncoder.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            MediaFormat videoFormat = null;
            long lastSegmentPointUS = 0;
            while (true) {
                int index = videoEncoder.dequeueOutputBuffer(bufferInfo, VIDEO_TIMEOUT_US);
                if (index >= 0) {
                    ByteBuffer encodedData = outputBuffers[index];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + index +
                                " was null");
                    }

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        bufferInfo.size = 0;
                    }

                    if (bufferInfo.size != 0) {
                        if (!readyForMuxing) {
                            throw new RuntimeException("muxer hasn't started");
                        }

                        Log.d(TAG, "presentation time: " + bufferInfo.presentationTimeUs);
                        // recorded 3 seconds, get a new mediamuxer, need to wait fot a key frame
                        if((bufferInfo.presentationTimeUs - lastSegmentPointUS > segmentDurationUS) &&
                                (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                            newMediaMuxer(videoFormat);
                            lastSegmentPointUS = bufferInfo.presentationTimeUs;
                        }
                        // write video sample to muxer
                        mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
                        if (VERBOSE) Log.d(TAG, "sent " + bufferInfo.size + " bytes to muxer");
                    }
                    videoEncoder.releaseOutputBuffer(index, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;      // out of while
                    }
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    // TODO: should not be necessary, try to remove these
                    // should happen before receiving buffers, and should only happen once
                    if (readyForMuxing) {
                        throw new RuntimeException("format changed twice");
                    }
                    videoFormat = videoEncoder.getOutputFormat();
                    newMediaMuxer(videoFormat);
                    Log.d(TAG, "encoder output format changed: " + videoFormat);
                    //prepareMediaMuxer(format);
                    readyForMuxing = true;
                    // now the format is known, start the muxer
//                    videoTrackIndex = mediaMuxer.addTrack(videoFormat);
//                    mediaMuxer.start();
                } else if(index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = videoEncoder.getOutputBuffers();
                } else {
                    Log.w(TAG, "encoder.dequeueOutputBuffer: " +
                            index);
                }
            }
            // TODO: stop encoder and muxer,
            videoEncoder.stop();
            mediaMuxer.stop();
        }
    }

    public boolean prepareCamera() {
        Log.d(TAG, "prepare camera");
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return false;
        }
        camera = Camera.open();
        Camera.Parameters parms = camera.getParameters();
        choosePreviewSize(parms, VIDEO_WIDTH, VIDEO_HEIGHT);
        Camera.Size size = parms.getPreviewSize();
        Log.d(TAG, "Camera preview size is " + size.width + "x" + size.height);
        // leave the frame rate set to default
        camera.setParameters(parms);

        // start rendering thread
        surfaceView.startGLThread();
        try {
            camera.setPreviewTexture(surfaceView.getSurfaceTexture());
        } catch (IOException e) {
            throw new RuntimeException("Invalid surface");
        }

        // start preview
        camera.startPreview();
        return true;
    }

    private void prepareAudioEncoder() {
        MediaFormat format  = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, AUDIO_SAMPLERATE, 1);
//        format.setInteger(
//                MediaFormat.KEY_AAC_PROFILE, kAACProfiles[k]);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1000);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        try {
            audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
        } catch (IOException e) {
//            throw new RuntimeException("Initializing audio encoder failed");
            e.printStackTrace();
        }
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
    }

    /**
     * Configures video encoder
     */
    private void prepareVideoEncoder() {
//        mBufferInfo = new MediaCodec.BufferInfo();
        Log.d(TAG, "preparing recorder");
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE_VIDEO, VIDEO_WIDTH, VIDEO_HEIGHT);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
//        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.
        try {
            videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE_VIDEO);
        } catch (IOException e) {
            throw new RuntimeException("creating video encoder failed");
        }
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        recordingSurface = videoEncoder.createInputSurface();
        videoEncoder.start();

    }

    public void startRecording() {
        readyForMuxing = false;
        newOutputDirectory();
        executorService.execute(new MediaMuxingTask());
        //executorService.execute(new AudioRecordingTask());
        //executorService.execute(new AudioMuxingTask());
        if(recordingSurface != null) {
            surfaceView.addMediaCodecSurface(recordingSurface);
        }
        mediaSegmentIndex = 0;
        isRecording = true;
    }

    public void stopRecording() {
        surfaceView.removeMediaCodecSurface();
        videoEncoder.signalEndOfInputStream();
        isRecording = false;
        //TODO: may need to restart preview

    }

    public void release() {
        // TODO: release resources
        // release camera
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        //
        if (videoEncoder != null) {
            videoEncoder.release();
            videoEncoder = null;
        }

        if (mediaMuxer != null) {
            mediaMuxer.release();
            mediaMuxer = null;
        }
        executorService.shutdown();
    }
}
