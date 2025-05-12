// MainActivity.java
package com.mario.musicplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ListView listView;
    private Button playButton, pauseButton, stopButton;
    private ShapeableImageView albumArt;
    private TextView titleText, artistText;
    private ArrayList<File> songList;
    private int currentSongIndex = -1;
    private final int REQUEST_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);
        playButton = findViewById(R.id.playButton);
        pauseButton = findViewById(R.id.pauseButton);
        stopButton = findViewById(R.id.stopButton);
        albumArt = findViewById(R.id.albumArt);
        titleText = findViewById(R.id.titleText);
        artistText = findViewById(R.id.artistText);

        playButton.setOnClickListener(v -> sendActionToService(MusicService.ACTION_RESUME));
        pauseButton.setOnClickListener(v -> sendActionToService(MusicService.ACTION_PAUSE));
        stopButton.setOnClickListener(v -> sendActionToService(MusicService.ACTION_STOP));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        } else {
            loadSongs();
        }
    }

    private void sendActionToService(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSongs() {
        File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
        songList = findSongs(musicDir);
        ArrayList<String> songNames = new ArrayList<>();

        for (File song : songList) {
            songNames.add(song.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songNames);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            currentSongIndex = position;
            File selectedSong = songList.get(currentSongIndex);

            extractMetadata(selectedSong.getAbsolutePath());

            Intent intent = new Intent(MainActivity.this, MusicService.class);
            intent.setAction(MusicService.ACTION_START);
            intent.putExtra("song_path", selectedSong.getAbsolutePath());
            startService(intent);
        });
    }

    private void extractMetadata(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);

        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        byte[] art = retriever.getEmbeddedPicture();

        titleText.setText(title != null ? title : "Unknown Title");
        artistText.setText(artist != null ? artist : "Unknown Artist");

        if (art != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
            albumArt.setImageBitmap(bitmap);
        } else {
            albumArt.setImageResource(R.drawable.ic_music_note); // fallback icon
        }
    }

    private ArrayList<File> findSongs(File dir) {
        ArrayList<File> songs = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    songs.addAll(findSongs(file));
                } else if (file.getName().endsWith(".mp3") || file.getName().endsWith(".m4a")) {
                    songs.add(file);
                }
            }
        }
        return songs;
    }
}
