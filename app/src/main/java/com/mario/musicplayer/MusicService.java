package com.mario.musicplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

public class MusicService extends Service {

    public static final String CHANNEL_ID = "MusicServiceChannel";
    public static final String ACTION_START = "com.mario.musicplayer.ACTION_START";
    public static final String ACTION_PAUSE = "com.mario.musicplayer.ACTION_PAUSE";
    public static final String ACTION_RESUME = "com.mario.musicplayer.ACTION_RESUME";
    public static final String ACTION_STOP = "com.mario.musicplayer.ACTION_STOP";

    private MediaPlayer mediaPlayer;
    private String currentSongName = "Unknown";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            String songPath = intent.getStringExtra("song_path");
            playSong(songPath);
        } else if (ACTION_PAUSE.equals(action)) {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                showNotification("Paused");
            }
        } else if (ACTION_RESUME.equals(action)) {
            if (mediaPlayer != null) {
                mediaPlayer.start();
                showNotification("Resumed: " + currentSongName);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void playSong(String path) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepare();
            mediaPlayer.start();

            currentSongName = new File(path).getName();
            showNotification("Playing: " + currentSongName);

            mediaPlayer.setOnCompletionListener(mp -> stopSelf());

        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    private void showNotification(String contentText) {
        Intent pauseIntent = new Intent(this, MusicService.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent resumeIntent = new Intent(this, MusicService.class);
        resumeIntent.setAction(ACTION_RESUME);
        PendingIntent resumePendingIntent = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, MusicService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Music Player")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true);

        startForeground(1, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}