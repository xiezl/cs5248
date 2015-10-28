package soc.cs5248;

import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


@SuppressWarnings("deprecation")
public class RecorderActivity extends ActionBarActivity {

    private final static String TAG = "RecorderActivity";

    private CameraPreview cameraPreview;

    private Camera camera;

    private MediaRecorder mediaRecorder;

    private boolean isRecording;

    //720p resolution.  h.264 encoding with 3Mbps bitrate.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recorder);
        camera = getCameraInstance();
        cameraPreview = new CameraPreview(this, camera);
        FrameLayout preViewLayout = (FrameLayout)findViewById(R.id.preview_layout);
        preViewLayout.addView(cameraPreview);
//        prepareRecorder();
    }


    private Camera getCameraInstance() {
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Camera camera = Camera.open();
            return camera;
        } else {
            return null;
        }
    }

    private boolean prepareRecorder() {
        mediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        camera.unlock();
        mediaRecorder.setCamera(camera);

        // Step 2: Set sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        // TODO: set video and audio encoder
//        // Step 3: Set output format and encoding (for versions prior to API Level 8)
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
//        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

        // TODO: set recording parameters
//        setVideoEncodingBitRate()
//        setVideoSize()
//        setVideoFrameRate()
//        setAudioEncodingBitRate()
//        setAudioChannels()
//        setAudioSamplingRate()

        // Step 4: Set output file
        mediaRecorder.setOutputFile(getOutputMediaFile().toString());

        // Step 5: Set the preview output
        mediaRecorder.setPreviewDisplay(cameraPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            camera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera() {
        if (camera != null){
            camera.release();        // release the camera for other applications
            camera = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_recorder, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_control) {
            if(isRecording) {
                // stop recording
                mediaRecorder.stop();  // stop the recording
                releaseMediaRecorder(); // release the MediaRecorder object
                camera.lock();         // take camera access back from MediaRecorder

                // inform the user that recording has stopped
                isRecording = false;
                item.setTitle("Start");
            } else {
                // start recording
                if (prepareRecorder()) {
                    // Camera is available and unlocked, MediaRecorder is prepared,
                    // now you can start recording
                    mediaRecorder.start();
                    // inform the user that recording has started
                    item.setTitle("Stop");
                    isRecording = true;
                } else {
                    // prepare didn't work, release the camera
                    releaseMediaRecorder();
                    // inform user
                    Toast.makeText(this, "Start recording failed", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Create a File for saving video */
    private static File getOutputMediaFile(){

        // TODO: change to internal storage
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "CS5248");
        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File videoFile;
        videoFile = new File(mediaStorageDir.getPath() + File.separator +
                timeStamp + ".mp4");
        return videoFile;
    }
}
