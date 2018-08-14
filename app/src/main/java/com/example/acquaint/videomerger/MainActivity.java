package com.example.acquaint.videomerger;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSION_WRITE_EXTERNAL_STORAGE = 100;
    private static final int REQUEST_GALLERY = 0;
    ArrayList<Uri> imageUri = new ArrayList<>();
    private VideoView videoView;
    private ProgressDialog progressDialog;
    private int count;
    private MediaController mediaController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videoView = findViewById(R.id.videoView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSION_WRITE_EXTERNAL_STORAGE);
            }
        }

        findViewById(R.id.btn_select_files).setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("video/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, REQUEST_GALLERY);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GALLERY) {
            if (resultCode == Activity.RESULT_OK) {
                imageUri.clear();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    int currentItem = 0;

                    while (currentItem < count) {
                        imageUri.add(data.getClipData().getItemAt(currentItem).getUri());
                        currentItem = currentItem + 1;
                    }

                    new MergeVideo().execute();

                } else if (data.getData() != null) {
                    String imagePath = data.getData().getPath();
                    imageUri.add(Uri.parse(imagePath));
                    Log.e(TAG, "onActivityResult: " + imagePath);
                    //do something with the image (save it to some directory or whatever you need to do with it here)
                }
            }
            super.onActivityResult(requestCode, resultCode, data);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    public class MergeVideo extends AsyncTask<String, Integer, String> {

        private String filename = "divya" + UUID.randomUUID() + ".mp4";
        private String outputDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath();

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(MainActivity.this,
                    "Preparing for upload", "Please wait...", true);
            // do initialization of required objects objects here
        }

        @Override
        protected String doInBackground(String... params) {
            List<Movie> inMovies = new ArrayList<>();
            for (Uri uri : imageUri) {
                try {
                    inMovies.add(MovieCreator.build(ImagePathMarshmallow.getPath(MainActivity.this, uri)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            List<Track> videoTracks = new LinkedList<>();
            List<Track> audioTracks = new LinkedList<>();

            for (Movie m : inMovies) {
                for (Track t : m.getTracks()) {
                    if (t.getHandler().equals("soun")) {
                        audioTracks.add(t);
                    }
                    if (t.getHandler().equals("vide")) {
                        videoTracks.add(t);
                    }
                }
            }

            Movie result = new Movie();

            if (audioTracks.size() > 0) {
                try {
                    result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
                } catch (final IOException e) {
                    Log.e(TAG, "doInBackground: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            if (videoTracks.size() > 0) {
                try {
                    result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
                } catch (final IOException e) {
                    Log.e(TAG, "doInBackground: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            Container out = new DefaultMp4Builder().build(result);
            WritableByteChannel wbc = null;

            try {
                wbc = new FileOutputStream(new File(outputDirectory, filename)).getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            try {
                out.writeContainer(wbc);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (wbc != null) {
                        wbc.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return outputDirectory + "/" + filename;
        }

        @Override
        protected void onPostExecute(String value) {
            super.onPostExecute(value);
            progressDialog.dismiss();
            Log.e(TAG, "onPostExecute: " + value);

            if (value != null) {
                mediaController = new MediaController(MainActivity.this);
                mediaController.setAnchorView(videoView);
                videoView.setMediaController(mediaController);
                videoView.setVideoPath(value);
                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        videoView.start();
                    }
                });
            }
        }
    }
}
