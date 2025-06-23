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
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media2.session.MediaSession; // Note: This is Media2, but we're using MediaSessionCompat from androidx.media
import androidx.media.session.MediaSessionCompat; // Correct import for MediaSessionCompat
import androidx.media.session.PlaybackStateCompat;
import androidx.media.session.MediaControllerCompat;
import androidx.media.session.MediaSessionCompat.Callback;
import androidx.media.MediaMetadataCompat;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private String currentSongIdentifier = null;
    private Bitmap currentAlbumArtBitmap = null;

    private DatabaseHelper db;

    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    private MediaMetadataCompat.Builder metadataBuilder;

    private ScheduledExecutorService scheduledExecutorService;
    private Handler handler = new Handler(Looper.getMainLooper()); // For UI updates on main thread

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
        db = new DatabaseHelper(this);

        // Initialize MediaSessionCompat
        ComponentName mediaButtonReceiver = new ComponentName(getApplicationContext(), MediaButtonReceiver.class);
        mediaSession = new MediaSessionCompat(getApplicationContext(), "MusicService", mediaButtonReceiver, null);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set initial PlaybackState
        playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO | // Crucial for seek bar
                        PlaybackStateCompat.ACTION_PLAY_PAUSE);
        mediaSession.setPlaybackState(playbackStateBuilder.build());

        // Set MediaSession Callback
        mediaSession.setCallback(new MediaSessionCallback());

        // Set initial MediaMetadata
        metadataBuilder = new MediaMetadataCompat.Builder();
        mediaSession.setMetadata(metadataBuilder.build());

        mediaSession.setActive(true);

        // Schedule periodic updates for playback position
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(this::updatePlaybackState, 0, 500, TimeUnit.MILLISECONDS);
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
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    stopForeground(true);
                    sendBroadcastUpdate("paused", currentSongIdentifier);
                }
                break;
            case ACTION_RESUME:
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    startForeground(1, createNotification());
                    sendBroadcastUpdate("resumed", currentSongIdentifier);
                }
                break;
            case ACTION_STOP:
                stopForeground(true);
                stopSelf();
                break;
            case ACTION_NEXT:
                // This action is now handled by MainActivity determining the next song
                // and sending a new ACTION_START intent.
                // We just need to notify MainActivity to play next.
                sendBroadcastUpdate("completed", currentSongIdentifier); // Simulate completion to trigger next song in MainActivity
                break;
            case ACTION_PREV:
                // Similar to ACTION_NEXT, notify MainActivity to play previous.
                sendBroadcastUpdate("previous", currentSongIdentifier); // Custom broadcast for previous
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
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
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
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                startForeground(1, createNotification());
                sendBroadcastUpdate("started", currentSongIdentifier);
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                sendBroadcastUpdate("completed", currentSongIdentifier);
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED); // Or STATE_SKIPPING_TO_NEXT
                stopForeground(true);
                stopSelf();
            });

        } catch (IOException e) {
            Toast.makeText(this, "IO Error playing song: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            stopForeground(true);
            stopSelf();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Invalid song data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            stopForeground(true);
            stopSelf();
        } catch (SecurityException e) {
            Toast.makeText(this, "Permission denied to play song: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            stopForeground(true);
            stopSelf();
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Player state error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
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

        // Update MediaMetadataCompat
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist);
        if (currentAlbumArtBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArtBitmap);
        } else {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
        }
        if (mediaPlayer != null) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration());
        }
        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void sendBroadcastUpdate(String status, @Nullable String songIdentifier) {
        Intent intent = new Intent("UPDATE_UI");
        intent.putExtra("status", status);
        if (songIdentifier != null) intent.putExtra("song_identifier", songIdentifier);
        sendBroadcast(intent);
    }

    private void updatePlaybackState() {
        if (mediaPlayer == null) return;

        int state = PlaybackStateCompat.STATE_STOPPED;
        if (mediaPlayer.isPlaying()) {
            state = PlaybackStateCompat.STATE_PLAYING;
        } else if (mediaPlayer.getCurrentPosition() > 0 && mediaPlayer.getDuration() > 0) {
            state = PlaybackStateCompat.STATE_PAUSED;
        }

        updatePlaybackState(state);
    }

    private void updatePlaybackState(int state) {
        long position = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        playbackStateBuilder.setState(state, position, 1.0f); // 1.0f is playback speed
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        // Update notification to reflect new state (e.g., play/pause button)
        startForeground(1, createNotification());
    }

    private Notification createNotification() {
        String channelId = "music_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        // Get current playback state to determine play/pause icon
        boolean isPlaying = mediaPlayer != null && mediaPlayer.isPlaying();
        int playPauseIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play; // Custom play/pause icons

        // Intents for notification actions
        PendingIntent prevIntent = PendingIntent.getService(this, 0,
                new Intent(this, MusicService.class).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent playPauseIntent = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(isPlaying ? ACTION_PAUSE : ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getService(this, 2,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE);

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
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show content on lock screen
                .setOnlyAlertOnce(true); // Prevent repeated sound/vibration for updates

        // Set large icon (album art)
        if (currentAlbumArtBitmap != null) {
            builder.setLargeIcon(currentAlbumArtBitmap);
        } else {
            // Fallback if no album art is available
            builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play));
        }

        // Add actions (buttons) - Order matters for compact view
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_prev, // Custom previous icon
                "Previous",
                prevIntent));
        builder.addAction(new NotificationCompat.Action(
                playPauseIcon, // Custom play/pause icon
                isPlaying ? "Pause" : "Play",
                playPauseIntent));
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_next, // Custom next icon
                "Next",
                nextIntent));

        // Apply MediaStyle and connect to MediaSession
        builder.setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2) // Show Prev, Play/Pause, Next in compact view
        );

        return builder.build();
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            super.onPlay();
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                startForeground(1, createNotification());
                sendBroadcastUpdate("resumed", currentSongIdentifier);
            } else if (mediaPlayer == null && currentSongIdentifier != null) {
                // If player is null but a song is selected, start playback
                startMediaPlayer(currentSongIdentifier);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                stopForeground(true); // Stop foreground to remove persistent notification
                sendBroadcastUpdate("paused", currentSongIdentifier);
            }
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            // Trigger next song in MainActivity
            sendBroadcastUpdate("completed", currentSongIdentifier);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            // Trigger previous song in MainActivity
            sendBroadcastUpdate("previous", currentSongIdentifier);
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
            if (mediaPlayer != null) {
                mediaPlayer.seekTo((int) pos);
                updatePlaybackState(); // Update state with new position
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
            stopForeground(true);
            stopSelf();
            sendBroadcastUpdate("stopped", currentSongIdentifier);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        sendBroadcastUpdate("stopped", currentSongIdentifier);
        currentSongIdentifier = null;
        currentAlbumArtBitmap = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mediaSession.getSessionToken().getBinder(); // Return binder for MediaControllerCompat
    }
}
