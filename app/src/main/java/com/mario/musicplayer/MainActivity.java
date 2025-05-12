package com.mario.musicplayer;

import android.Manifest;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.animation.*;
import android.widget.*;
import android.content.pm.PackageManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.*;
import java.util.*;

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
    private SharedPreferences prefs;
    private boolean isPlaying = false;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            String path = intent.getStringExtra("song_path");

            if ("paused".equals(status)) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                albumArt.clearAnimation();
                isPlaying = false;
            } else if ("resumed".equals(status) || "started".equals(status)) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                albumArt.startAnimation(rotateAnim);
                isPlaying = true;
                startSeekBarUpdate();
            } else if ("next".equals(status) && path != null) {
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
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    MediaPlayer player = MusicService.getMediaPlayer();
                    if (player != null) player.seekTo(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
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

    // Check latest playing song index and update UI
    int index = prefs.getInt("last_index", -1);
    if (index != -1 && songList != null && index < songList.size()) {
        currentSongIndex = index;
        extractMetadata(songList.get(index).getAbsolutePath());
        }
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
            } else {
                sendActionToService(MusicService.ACTION_RESUME);
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
        ArrayList<String> names = new ArrayList<>();
        for (File f : songList) names.add(f.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);

        int lastIndex = prefs.getInt("last_index", -1);
        if (lastIndex >= 0 && lastIndex < songList.size()) {
            currentSongIndex = lastIndex;
            extractMetadata(songList.get(currentSongIndex).getAbsolutePath());
        }

        listView.setOnItemClickListener((parent, view, pos, id) -> {
            currentSongIndex = pos;
            playCurrentSong();
        });
    }

    private void playCurrentSong() {
        File song = songList.get(currentSongIndex);
        extractMetadata(song.getAbsolutePath());
        prefs.edit().putInt("last_index", currentSongIndex).apply();

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_START);
        intent.putExtra("song_path", song.getAbsolutePath());
        startService(intent);

        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        albumArt.startAnimation(rotateAnim);
        isPlaying = true;
        startSeekBarUpdate();
    }

    private void playNext() {
        if (songList == null || songList.isEmpty()) return;
        currentSongIndex = (currentSongIndex + 1) % songList.size();
        playCurrentSong();
    }

    private void playPrevious() {
        if (songList == null || songList.isEmpty()) return;
        currentSongIndex = (currentSongIndex - 1 + songList.size()) % songList.size();
        playCurrentSong();
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
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            byte[] art = retriever.getEmbeddedPicture();

            titleText.setText(title != null ? title : "Unknown Title");
            artistText.setText(artist != null ? artist : "Unknown Artist");

            if (duration != null) {
                int ms = Integer.parseInt(duration);
                seekBar.setMax(ms);
                durationText.setText(millisecondsToTimer(ms));
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

    private String millisecondsToTimer(int ms) {
        int mins = (ms / 1000) / 60;
        int secs = (ms / 1000) % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private ArrayList<File> findSongs(File dir) {
        ArrayList<File> songs = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) songs.addAll(findSongs(f));
                else if (f.getName().endsWith(".mp3") || f.getName().endsWith(".m4a")) songs.add(f);
            }
        }
        return songs;
    }
}