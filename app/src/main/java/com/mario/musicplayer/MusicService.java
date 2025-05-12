package com.mario.musicplayer;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.util.*;

public class MusicService extends Service {

    public static final String ACTION_START = "START";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREV";

    private MediaPlayer mediaPlayer;
    private static MusicService instance;
    private String currentTitle = "Music Playing";
    private String currentArtist = "Enjoy your music";
    private ArrayList<File> songList = new ArrayList<>();
    private int currentIndex = -1;
    private SharedPreferences prefs;

    public static MediaPlayer getMediaPlayer() {
        return instance != null ? instance.mediaPlayer : null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE);
        loadSongs();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            String path = intent.getStringExtra("song_path");
            currentIndex = prefs.getInt("last_index", -1);
            extractMetadata(path);
            startMediaPlayer(path);

            sendBroadcastUpdate("started", path);
        } else if (ACTION_PAUSE.equals(action)) {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                stopForeground(true);
                sendBroadcastUpdate("paused", null);
            }
        } else if (ACTION_RESUME.equals(action)) {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                startForeground(1, createNotification());
                sendBroadcastUpdate("resumed", null);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
        } else if (ACTION_NEXT.equals(action)) {
            currentIndex = (currentIndex + 1) % songList.size();
            playSongAt(currentIndex);
        } else if (ACTION_PREV.equals(action)) {
            currentIndex = (currentIndex - 1 + songList.size()) % songList.size();
            playSongAt(currentIndex);
        }

        return START_STICKY;
    }

    private void startMediaPlayer(String path) {
        if (mediaPlayer != null) mediaPlayer.reset();
        else mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();

            startForeground(1, createNotification());

            mediaPlayer.setOnCompletionListener(mp -> {
                currentIndex = prefs.getInt("last_index", -1) + 1;
                if (currentIndex < songList.size()) {
                    prefs.edit().putInt("last_index", currentIndex).apply();
                    String nextPath = songList.get(currentIndex).getAbsolutePath();
                    extractMetadata(nextPath);
                    startMediaPlayer(nextPath);
                    sendBroadcastUpdate("next", nextPath);
                } else {
                    stopForeground(true);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playSongAt(int index) {
        if (index < 0 || index >= songList.size()) return;
        File file = songList.get(index);
        extractMetadata(file.getAbsolutePath());
        prefs.edit().putInt("last_index", index).apply();
        startMediaPlayer(file.getAbsolutePath());
        sendBroadcastUpdate("next", file.getAbsolutePath());
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

    private void sendBroadcastUpdate(String status, String path) {
        Intent intent = new Intent("UPDATE_UI");
        intent.putExtra("status", status);
        if (path != null) intent.putExtra("song_path", path);
        sendBroadcast(intent);
    }

    private Notification createNotification() {
        String channelId = "music_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        PendingIntent prevIntent = PendingIntent.getService(this, 0,
                new Intent(this, MusicService.class).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pauseIntent = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getService(this, 2,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setSmallIcon(R.drawable.ic_music_note)
                .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
                .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .setOngoing(true)
                .build();
    }

    private void loadSongs() {
        File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
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