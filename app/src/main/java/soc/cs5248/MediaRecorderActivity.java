package soc.cs5248;


import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import soc.cs5248.recorder.MediaRecorder;
import soc.cs5248.recorder.libstreaming.SurfaceView;


@SuppressWarnings("deprecation")
public class MediaRecorderActivity extends ActionBarActivity{

    private final static String TAG = "MediaRecorderActivity";

    private MediaRecorder mediaRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_recorder);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.camera_preview_view);
        mediaRecorder = new MediaRecorder(this, surfaceView);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_media_recorder, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_control) {
            if(mediaRecorder.isRecording()) {
                mediaRecorder.stopRecording();
                item.setTitle("Start");
            } else {
                mediaRecorder.startRecording();
                item.setTitle("Stop");
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    protected void onStop() {
        super.onStop();
        mediaRecorder.release();
    }
}
