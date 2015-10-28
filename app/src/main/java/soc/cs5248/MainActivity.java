package soc.cs5248;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {
    private final static String TAG = "MainActivity";

    private ListView videoListView;

    private List<String> videoList;

    private void prepareVideoList() {
        Log.d(TAG, "prepare video list");
        File videoFolder = new File(getFilesDir(), "videos");
        if(!videoFolder.exists()) {
            videoFolder.mkdir();
        }
        videoList = new ArrayList<>();
        for(File video : videoFolder.listFiles()) {
            videoList.add(video.getName());
            Log.d(TAG, "video:" + video.getName());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoListView = (ListView) findViewById(R.id.video_list_view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        prepareVideoList();
        videoListView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, videoList));
        videoListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // ListView Clicked item value
                String videoName = videoList.get(i);
                Intent intent = new Intent(MainActivity.this, MediaPlayerActivity.class);
                intent.putExtra("video_name", videoName);
                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_record) {
            Intent intent = new Intent(MainActivity.this, MediaRecorderActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }
}
