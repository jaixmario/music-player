package com.mario.musicplayer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "music_meta.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SONGS = "songs";
    public static final String COL_PATH = "path";
    public static final String COL_TITLE = "title";
    public static final String COL_ARTIST = "artist";
    public static final String COL_DURATION = "duration";
    public static final String COL_ART = "album_art"; // Stored as BLOB

    private static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_SONGS + " (" +
                    COL_PATH + " TEXT PRIMARY KEY, " +
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

    public boolean isSongCached(String path) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SONGS, new String[]{COL_PATH},
                COL_PATH + "=?", new String[]{path},
                null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public void insertSong(String path, String title, String artist, int duration, byte[] albumArt) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PATH, path);
        values.put(COL_TITLE, title);
        values.put(COL_ARTIST, artist);
        values.put(COL_DURATION, duration);
        values.put(COL_ART, albumArt);
        db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor getSong(String path) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_SONGS, null,
                COL_PATH + "=?", new String[]{path},
                null, null, null);
    }
}