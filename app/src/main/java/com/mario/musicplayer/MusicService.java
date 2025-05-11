package com.mario.musicplayer;

import android.app.*;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

public class MusicService extends Service {

    public static final String ACTION_START = "com.mario.musicplayer.ACTION_START";
    public static final String CHANNEL_ID = "music_channel";
    private MediaPlayer mediaPlayer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_START.equals(intent.getAction())) {
            String path = intent.getStringExtra("path");
            playMusic(path);
        }
        return START_NOT_STICKY;
    }

    private void playMusic(String path) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        mediaPlayer = MediaPlayer.create(this, Uri.fromFile(new File(path)));
        mediaPlayer.start();

        showNotification(new File(path).getName());

        mediaPlayer.setOnCompletionListener(mp -> stopSelf());
    }

    private void showNotification(String title) {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Now Playing")
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_music_note)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null)
                manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}