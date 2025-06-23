package com.mario.musicplayer;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.*;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.widget.Toast;
import android.database.Cursor;
import android.net.Uri;
import androidx.media.app.NotificationCompat.MediaStyle; // Import MediaStyle

import java.io.IOException;

public class MusicService extends Service {

    public static final String ACTION_START = "START";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREV";
    public static final String ACTION_LIKE = "LIKE"; // New action
    public static final String ACTION_DISLIKE = "DISLIKE"; // New action

    private MediaPlayer mediaPlayer;
    private static MusicService instance;
    private String currentTitle = "Music Playing";
    private String currentArtist = "Enjoy your music";
    private String currentSongIdentifier = null;
    private Bitmap currentAlbumArtBitmap = null; // To store album art for notification

    private DatabaseHelper db;

    public static MediaPlayer getMediaPlayer() {
        return instance != null ? instance.mediaPlayer : null;
    }

    public static String getCurrentSongIdentifier() {
        return (instance != null) ? instance.currentSongIdentifier : null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = new DatabaseHelper(this); // Corrected to DatabaseHelper
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        switch (action) {
            case ACTION_START:
                String songIdentifier = intent.getStringExtra("song_identifier");
                if (songIdentifier != null) {
                    currentSongIdentifier = songIdentifier;
                    extractMetadata(currentSongIdentifier);
                    startMediaPlayer(currentSongIdentifier);
                    sendBroadcastUpdate("started", currentSongIdentifier);
                } else {
                    Toast.makeText(this, "Error: No song identifier provided to service.", Toast.LENGTH_LONG).show();
                    stopSelf();
                }
                break;
            case ACTION_PAUSE:
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    stopForeground(true);
                    sendBroadcastUpdate("paused", currentSongIdentifier);
                }
                break;
            case ACTION_RESUME:
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    startForeground(1, createNotification());
                    sendBroadcastUpdate("resumed", currentSongIdentifier);
                }
                break;
            case ACTION_STOP:
                stopForeground(true);
                stopSelf();
                break;
            case ACTION_NEXT:
                // These actions are now handled by MainActivity determining the next/previous song
                // and sending a new ACTION_START intent.
                // This part of the service will likely not be reached if MainActivity is updated correctly.
                Toast.makeText(this, "Next action received (handled by MainActivity).", Toast.LENGTH_SHORT).show();
                break;
            case ACTION_PREV:
                Toast.makeText(this, "Previous action received (handled by MainActivity).", Toast.LENGTH_SHORT).show();
                break;
            case ACTION_LIKE:
                Toast.makeText(this, "Liked!", Toast.LENGTH_SHORT).show();
                // Implement your like logic here (e.g., update database, send to server)
                break;
            case ACTION_DISLIKE:
                Toast.makeText(this, "Disliked!", Toast.LENGTH_SHORT).show();
                // Implement your dislike logic here
                break;
        }

        return START_STICKY;
    }

    private void startMediaPlayer(String songIdentifier) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Toast.makeText(this, "Playback error: " + what + ", " + extra, Toast.LENGTH_LONG).show();
                    sendBroadcastUpdate("stopped", currentSongIdentifier);
                    stopForeground(true);
                    stopSelf();
                    return true;
                });
            }

            if (songIdentifier.startsWith("content://")) {
                mediaPlayer.setDataSource(this, Uri.parse(songIdentifier));
            } else {
                mediaPlayer.setDataSource(songIdentifier);
            }
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                startForeground(1, createNotification());
                sendBroadcastUpdate("started", currentSongIdentifier);
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                sendBroadcastUpdate("completed", currentSongIdentifier);
                stopForeground(true);
                stopSelf();
            });

        } catch (IOException e) {
            Toast.makeText(this, "IO Error playing song: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            stopForeground(true);
            stopSelf();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Invalid song data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            stopForeground(true);
            stopSelf();
        } catch (SecurityException e) {
            Toast.makeText(this, "Permission denied to play song: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            stopForeground(true);
            stopSelf();
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Player state error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            stopForeground(true);
            stopSelf();
        }
    }

    private void extractMetadata(String songIdentifier) {
        Cursor cursor = db.getSong(songIdentifier);
        if (cursor != null && cursor.moveToFirst()) {
            currentTitle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            currentArtist = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ARTIST));
            byte[] art = cursor.getBlob(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ART));
            if (art != null) {
                currentAlbumArtBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
            } else {
                currentAlbumArtBitmap = null;
            }
        } else {
            currentTitle = "Unknown Title";
            currentArtist = "Unknown Artist";
            currentAlbumArtBitmap = null;
            if (songIdentifier != null) {
                int lastSlash = songIdentifier.lastIndexOf('/');
                if (lastSlash != -1 && lastSlash < songIdentifier.length() - 1) {
                    currentTitle = songIdentifier.substring(lastSlash + 1);
                } else {
                    currentTitle = songIdentifier;
                }
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private void sendBroadcastUpdate(String status, @Nullable String songIdentifier) {
        Intent intent = new Intent("UPDATE_UI");
        intent.putExtra("status", status);
        if (songIdentifier != null) intent.putExtra("song_identifier", songIdentifier);
        sendBroadcast(intent);
    }

    private Notification createNotification() {
        String channelId = "music_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        // Intents for notification actions
        PendingIntent prevIntent = PendingIntent.getService(this, 0,
                new Intent(this, MusicService.class).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pauseIntent = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getService(this, 2,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent likeIntent = PendingIntent.getService(this, 3,
                new Intent(this, MusicService.class).setAction(ACTION_LIKE), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dislikeIntent = PendingIntent.getService(this, 4,
                new Intent(this, MusicService.class).setAction(ACTION_DISLIKE), PendingIntent.FLAG_IMMUTABLE);


        // Content intent to open MainActivity when notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setSmallIcon(R.drawable.ic_music_note) // Make sure you have this drawable
                .setSubText("MARIO 2.0") // Your app name or device name
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC); // Show content on lock screen

        // Set large icon (album art)
        if (currentAlbumArtBitmap != null) {
            builder.setLargeIcon(currentAlbumArtBitmap);
        } else {
            // Fallback if no album art is available
            builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play));
        }

        // Add actions (buttons)
        builder.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_previous, // Placeholder, replace with R.drawable.ic_prev if you have one
                "Previous",
                prevIntent));
        builder.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_pause, // Placeholder, replace with R.drawable.ic_play_pause if you have one
                "Pause",
                pauseIntent));
        builder.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_next, // Placeholder, replace with R.drawable.ic_next if you have one
                "Next",
                nextIntent));
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_thumb_down, // You need to create this drawable
                "Dislike",
                dislikeIntent));
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_thumb_up, // You need to create this drawable
                "Like",
                likeIntent));


        // Apply MediaStyle
        builder.setStyle(new MediaStyle()
                .setShowActionsInCompactView(1, 2) // Show Pause/Play and Next in compact view
                // If you implement MediaSessionCompat, you would set its token here:
                // .setMediaSession(mediaSession.getSessionToken())
        );

        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sendBroadcastUpdate("stopped", currentSongIdentifier);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentSongIdentifier = null;
        currentAlbumArtBitmap = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
