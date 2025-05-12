package com.mario.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.io.File;

public class MusicService extends Service {

    public static final String ACTION_START = "START";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_STOP = "STOP";

    private MediaPlayer mediaPlayer;
    private static MusicService instance;

    private String currentTitle = "Music Playing";
    private String currentArtist = "Enjoy your music";

    private SharedPreferences prefs;
    private ArrayList<File> songList = new ArrayList<>();
    private int currentIndex = -1;

    public static MediaPlayer getMediaPlayer() {
        return instance != null ? instance.mediaPlayer : null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            String path = intent.getStringExtra("song_path");
            currentIndex = prefs.getInt("last_index", -1);
            extractMetadata(path);
            startMediaPlayer(path);
        } else if (ACTION_PAUSE.equals(action)) {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                stopForeground(true); // remove notification
            }
        } else if (ACTION_RESUME.equals(action)) {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                startForeground(1, createNotification());
            }
        } else if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
        }

        return START_STICKY;
    }

    private void startMediaPlayer(String path) {
        if (mediaPlayer != null) {
            mediaPlayer.reset();
        } else {
            mediaPlayer = new MediaPlayer();
        }

        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();

            startForeground(1, createNotification());

            mediaPlayer.setOnCompletionListener(mp -> {
                loadSongs();
                int nextIndex = prefs.getInt("last_index", -1) + 1;
                if (nextIndex < songList.size()) {
                    prefs.edit().putInt("last_index", nextIndex).apply();
                    String nextPath = songList.get(nextIndex).getAbsolutePath();
                    extractMetadata(nextPath);
                    startMediaPlayer(nextPath);
                } else {
                    stopForeground(true);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void extractMetadata(String path) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

            currentTitle = title != null ? title : "Playing Song";
            currentArtist = artist != null ? artist : path.substring(path.lastIndexOf('/') + 1);

            retriever.release();
        } catch (Exception e) {
            currentTitle = "Unknown Song";
            currentArtist = "Unknown Artist";
        }
    }

    private Notification createNotification() {
        String channelId = "music_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setSmallIcon(R.drawable.ic_music_note)
                .setOngoing(true)
                .build();
    }

    private void loadSongs() {
        File musicDir = new File(android.os.Environment.getExternalStorageDirectory(), "Music");
        songList.clear();
        findSongs(musicDir);
    }

    private void findSongs(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) findSongs(file);
                else if (file.getName().endsWith(".mp3") || file.getName().endsWith(".m4a")) {
                    songList.add(file);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}