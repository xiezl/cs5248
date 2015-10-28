package soc.cs5248.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * download data file from network and feed into MediaExtractor
 */

public class DataSource {

    //private static Uri INIT_DATA = Uri.parse("android.resource://com.tbenze.mediaplayer/" + R.raw.init);

    private final static String TAG = "DataSource";

    private List<File> mediaFileList = new ArrayList<>();

    private File mediaDirectory;

    private File initFile;


    private int mediaIndex = 0;

    private Context context;

    public DataSource(Context context, String videoName) {
        this.context = context;

        mediaDirectory = new File(context.getFilesDir(), "videos");

        File mediaFolder = new File(mediaDirectory, videoName);
        for(File media : mediaFolder.listFiles()) {
            Log.d(TAG, "media file: " + media.getAbsolutePath());
            mediaFileList.add(media);
        }
    }

    /** return uri of next media, null if no more media available */
    public String getNextMedia() {
        if(mediaIndex < mediaFileList.size()) {
            File file = mediaFileList.get(mediaIndex++);
            return file.getAbsolutePath();

        }
        return null;
    }


}
