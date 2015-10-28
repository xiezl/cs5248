package soc.cs5248;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;

import soc.cs5248.player.DataSource;
import soc.cs5248.player.MediaPlayer;


public class MediaPlayerActivity extends ActionBarActivity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;

    private MediaPlayer mediaPlayer;

    private DataSource videoSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        surfaceView = (SurfaceView) findViewById(R.id.player_surface_view);
        surfaceView.getHolder().addCallback(this);
        mediaPlayer = new MediaPlayer(this);
        Intent intent = getIntent();
        if(intent != null) {
            String videoName = intent.getStringExtra("video_name");
            videoSource = new DataSource(this, videoName);
        } else {
            Toast.makeText(this, "no video selected", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    private void startPlaying() {
        try {
            mediaPlayer.setDataSource(videoSource);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

//        if(id == R.id.action_player_control) {
//            Toast.makeText(this, "start playing", Toast.LENGTH_SHORT).show();
//            startPlaying();
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    /**----------------SurfaceHolder CallBack-------------------*/
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mediaPlayer.setVideoSurface(holder.getSurface());
        startPlaying();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
