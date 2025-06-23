package com.mario.musicplayer;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import android.net.Uri;
import android.database.Cursor;
import android.provider.MediaStore;

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
    private ArrayList<String> songList = new ArrayList<>();
    public int currentIndex = -1;
    private SharedPreferences prefs;

    private DatabaseHelper db;

    public static MediaPlayer getMediaPlayer() {
        return instance != null ? instance.mediaPlayer : null;
    }

    public static String getCurrentSongIdentifier() {
        return (instance != null &&
                instance.mediaPlayer != null &&
                instance.currentIndex >= 0 &&
                instance.currentIndex < instance.songList.size())
                ? instance.songList.get(instance.currentIndex)
                : null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = new DatabaseHelper(this);
        prefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE);
        loadSongs();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            String songIdentifier = intent.getStringExtra("song_identifier");
            if (songIdentifier != null) {
                currentIndex = findIndexByIdentifier(songIdentifier);
                prefs.edit().putInt("last_index", currentIndex).apply();
                extractMetadata(songIdentifier);
                startMediaPlayer(songIdentifier);
                sendBroadcastUpdate("started", songIdentifier);
            }

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

    private void startMediaPlayer(String songIdentifier) {
        try {
            if (mediaPlayer != null) mediaPlayer.reset();
            else mediaPlayer = new MediaPlayer();

            if (songIdentifier.startsWith("content://")) {
                mediaPlayer.setDataSource(this, Uri.parse(songIdentifier));
            } else {
                mediaPlayer.setDataSource(songIdentifier);
            }

            mediaPlayer.prepare();
            mediaPlayer.start();

            startForeground(1, createNotification());

            mediaPlayer.setOnCompletionListener(mp -> {
                currentIndex++;
                if (currentIndex >= songList.size()) {
                    sendBroadcastUpdate("stopped", null);
                    stopForeground(true);
                    stopSelf();
                } else {
                    prefs.edit().putInt("last_index", currentIndex).apply();
                    String nextSongIdentifier = songList.get(currentIndex);
                    extractMetadata(nextSongIdentifier);
                    startMediaPlayer(nextSongIdentifier);
                    sendBroadcastUpdate("next", nextSongIdentifier);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playSongAt(int index) {
        if (index < 0 || index >= songList.size()) return;
        String songIdentifier = songList.get(index);
        currentIndex = index;
        prefs.edit().putInt("last_index", index).apply();
        extractMetadata(songIdentifier);
        startMediaPlayer(songIdentifier);
        sendBroadcastUpdate("next", songIdentifier);
    }

    private void extractMetadata(String songIdentifier) {
        Cursor cursor = db.getSong(songIdentifier);
        if (cursor.moveToFirst()) {
            currentTitle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            currentArtist = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ARTIST));
        } else {
            currentTitle = "Unknown Title";
            currentArtist = "Unknown Artist";
        }
        cursor.close();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            loadSongsFromMediaStore();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String storedUriString = prefs.getString("music_folder_uri", null);
            if (storedUriString != null) {
                Uri storedUri = Uri.parse(storedUriString);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, storedUri);
                if (pickedDir != null && pickedDir.isDirectory()) {
                    findSongsInDocumentFile(pickedDir);
                }
            }
        } else {
            File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
            findSongsInFile(musicDir);
        }
    }

    private void loadSongsFromMediaStore() {
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA
        };

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                null,
                null,
                null
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    songList.add(contentUri.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void findSongsInFile(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) findSongsInFile(file);
                else if (file.getName().endsWith(".mp3") || file.getName().endsWith(".m4a")) {
                    songList.add(file.getAbsolutePath());
                }
            }
        }
    }

    private void findSongsInDocumentFile(DocumentFile dir) {
        for (DocumentFile file : dir.listFiles()) {
            if (file.isDirectory()) {
                findSongsInDocumentFile(file);
            } else if (file.isFile() && (file.getName().endsWith(".mp3") || file.getName().endsWith(".m4a"))) {
                songList.add(file.getUri().toString());
            }
        }
    }

    private int findIndexByIdentifier(String identifier) {
        for (int i = 0; i < songList.size(); i++) {
            if (songList.get(i).equals(identifier)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sendBroadcastUpdate("stopped", null);
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
