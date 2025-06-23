package com.mario.musicplayer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList; // Added for ArrayList

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "music_meta.db"; // Retained your database name
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SONGS = "songs";
    public static final String COL_PATH = "path"; // Retained COL_PATH as identifier
    public static final String COL_TITLE = "title";
    public static final String COL_ARTIST = "artist";
    public static final String COL_DURATION = "duration";
    public static final String COL_ART = "album_art"; // Stored as BLOB

    private static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_SONGS + " (" +
                    COL_PATH + " TEXT PRIMARY KEY, " + // COL_PATH as PRIMARY KEY
                    COL_TITLE + " TEXT, " +
                    COL_ARTIST + " TEXT, " +
                    COL_DURATION + " INTEGER, " +
                    COL_ART + " BLOB" +
            ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
        onCreate(db);
    }

    // Inner class to hold song metadata for batch processing
    public static class SongMetadata {
        public String identifier; // Corresponds to COL_PATH
        public String title;
        public String artist;
        public int duration;
        public byte[] albumArt;

        public SongMetadata(String identifier, String title, String artist, int duration, byte[] albumArt) {
            this.identifier = identifier;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.albumArt = albumArt;
        }
    }

    public boolean isSongCached(String path) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        boolean exists = false;
        try {
            cursor = db.query(TABLE_SONGS, new String[]{COL_PATH},
                    COL_PATH + "=?", new String[]{path},
                    null, null, null);
            exists = cursor.moveToFirst();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            // Do not close db here, it's managed by SQLiteOpenHelper
        }
        return exists;
    }

    // Your original insertSong method, using CONFLICT_REPLACE
    public void insertSong(String path, String title, String artist, int duration, byte[] albumArt) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PATH, path);
        values.put(COL_TITLE, title);
        values.put(COL_ARTIST, artist);
        values.put(COL_DURATION, duration);
        values.put(COL_ART, albumArt);
        db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        // Do not close db here, it's managed by SQLiteOpenHelper
    }

    // New method for batch insertion of songs, using CONFLICT_REPLACE
    public void insertSongsBatch(ArrayList<SongMetadata> metadataList) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction(); // Start transaction for performance
        try {
            for (SongMetadata metadata : metadataList) {
                ContentValues values = new ContentValues();
                values.put(COL_PATH, metadata.identifier);
                values.put(COL_TITLE, metadata.title);
                values.put(COL_ARTIST, metadata.artist);
                values.put(COL_DURATION, metadata.duration);
                values.put(COL_ART, metadata.albumArt);
                db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful(); // Mark transaction as successful
        } finally {
            db.endTransaction(); // End transaction (commits if successful, rolls back otherwise)
            // db.close(); // Close db after a batch operation if no more operations are expected soon
        }
    }

    public Cursor getSong(String path) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_SONGS, null, // null for all columns
                COL_PATH + "=?", new String[]{path},
                null, null, null);
        // Do not close db here, it's managed by SQLiteOpenHelper
    }
}
