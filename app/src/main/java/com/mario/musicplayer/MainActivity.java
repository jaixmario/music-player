package com.mario.musicplayer;

import android.Manifest;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ListView listView;
    private ImageButton playPauseButton, nextButton, prevButton;
    private ShapeableImageView albumArt;
    private TextView titleText, artistText, currentTimeText, durationText;
    private SeekBar seekBar;
    private ArrayList<File> songList;
    private int currentSongIndex = -1;
    private final int REQUEST_PERMISSION = 1001;
    private Handler handler = new Handler();
    private Animation rotateAnim;
    private boolean isPlaying = false;
    private SharedPreferences prefs;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("UPDATE_UI".equals(intent.getAction())) {
                String path = intent.getStringExtra("song_path");
                currentSongIndex = prefs.getInt("last_index", -1);
                extractMetadata(path);
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                albumArt.startAnimation(rotateAnim);
                isPlaying = true;
                startSeekBarUpdate();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE);

        listView = findViewById(R.id.listView);
        playPauseButton = findViewById(R.id.playPauseButton);
        nextButton = findViewById(R.id.nextButton);
        prevButton = findViewById(R.id.prevButton);
        albumArt = findViewById(R.id.albumArt);
        titleText = findViewById(R.id.titleText);
        artistText = findViewById(R.id.artistText);
        currentTimeText = findViewById(R.id.currentTime);
        durationText = findViewById(R.id.totalTime);
        seekBar = findViewById(R.id.seekBar);
        rotateAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_album);

        playPauseButton.setOnClickListener(v -> togglePlayPause());
        nextButton.setOnClickListener(v -> playNext());
        prevButton.setOnClickListener(v -> playPrevious());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MediaPlayer player = MusicService.getMediaPlayer();
                    if (player != null) player.seekTo(progress);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        } else {
            loadSongs();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(updateReceiver, new IntentFilter("UPDATE_UI"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateReceiver);
    }

    private void togglePlayPause() {
        MediaPlayer player = MusicService.getMediaPlayer();
        if (player != null) {
            if (player.isPlaying()) {
                sendActionToService(MusicService.ACTION_PAUSE);
                playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                albumArt.clearAnimation();
                isPlaying = false;
            } else {
                sendActionToService(MusicService.ACTION_RESUME);
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                albumArt.startAnimation(rotateAnim);
                isPlaying = true;
            }
        }
    }

    private void sendActionToService(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void loadSongs() {
        File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
        songList = findSongs(musicDir);
        ArrayList<String> songNames = new ArrayList<>();
        for (File song : songList) songNames.add(song.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songNames);
        listView.setAdapter(adapter);

        int savedIndex = prefs.getInt("last_index", -1);
        if (savedIndex >= 0 && savedIndex < songList.size()) {
            currentSongIndex = savedIndex;
            extractMetadata(songList.get(currentSongIndex).getAbsolutePath());
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            albumArt.startAnimation(rotateAnim);
            isPlaying = true;
            startSeekBarUpdate();
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            currentSongIndex = position;
            playCurrentSong();
        });
    }

    private void playCurrentSong() {
        if (currentSongIndex < 0 || currentSongIndex >= songList.size()) return;
        File selectedSong = songList.get(currentSongIndex);
        extractMetadata(selectedSong.getAbsolutePath());

        prefs.edit().putInt("last_index", currentSongIndex).apply();

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_START);
        intent.putExtra("song_path", selectedSong.getAbsolutePath());
        startService(intent);

        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        albumArt.startAnimation(rotateAnim);
        isPlaying = true;
        startSeekBarUpdate();
    }

    private void playNext() {
        if (songList != null && !songList.isEmpty()) {
            currentSongIndex = (currentSongIndex + 1) % songList.size();
            playCurrentSong();
        }
    }

    private void playPrevious() {
        if (songList != null && !songList.isEmpty()) {
            currentSongIndex = (currentSongIndex - 1 + songList.size()) % songList.size();
            playCurrentSong();
        }
    }

    private void startSeekBarUpdate() {
        handler.postDelayed(new Runnable() {
            public void run() {
                MediaPlayer player = MusicService.getMediaPlayer();
                if (player != null && player.isPlaying()) {
                    int currentPos = player.getCurrentPosition();
                    seekBar.setProgress(currentPos);
                    currentTimeText.setText(millisecondsToTimer(currentPos));
                }
                handler.postDelayed(this, 500);
            }
        }, 0);
    }

    private void extractMetadata(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            byte[] art = retriever.getEmbeddedPicture();

            titleText.setText(title != null ? title : "Unknown Title");
            artistText.setText(artist != null ? artist : "Unknown Artist");

            if (durationStr != null) {
                int durationMs = Integer.parseInt(durationStr);
                seekBar.setMax(durationMs);
                durationText.setText(millisecondsToTimer(durationMs));
            }

            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                albumArt.setImageBitmap(bitmap);
            } else {
                albumArt.setImageResource(android.R.drawable.ic_media_play);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String millisecondsToTimer(int milliseconds) {
        int minutes = (milliseconds / 1000) / 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private ArrayList<File> findSongs(File dir) {
        ArrayList<File> songs = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) songs.addAll(findSongs(file));
                else if (file.getName().endsWith(".mp3") || file.getName().endsWith(".m4a"))
                    songs.add(file);
            }
        }
        return songs;
    }
}