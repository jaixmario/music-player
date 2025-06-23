package com.mario.musicplayer;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;

public class SongAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<String> songs; // Changed to ArrayList<String>
    private LayoutInflater inflater;
    private DatabaseHelper db;

    public SongAdapter(Context context, ArrayList<String> songs) { // Changed to ArrayList<String>
        this.context = context;
        this.songs = songs;
        inflater = LayoutInflater.from(context);
        db = new DatabaseHelper(context);
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Object getItem(int position) {
        return songs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        ImageView songImage;
        TextView songTitle;
        TextView songArtist;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        String songIdentifier = songs.get(position); // Changed to String

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.song_item, parent, false);
            holder = new ViewHolder();
            holder.songImage = convertView.findViewById(R.id.songImage);
            holder.songTitle = convertView.findViewById(R.id.songTitle);
            holder.songArtist = convertView.findViewById(R.id.songArtist);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Cursor cursor = db.getSong(songIdentifier); // Use songIdentifier
        if (cursor.moveToFirst()) {
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            String artist = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ARTIST));
            byte[] art = cursor.getBlob(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ART));

            holder.songTitle.setText(title != null ? title : "Unknown Title");
            holder.songArtist.setText(artist != null ? artist : "Unknown Artist");

            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                holder.songImage.setImageBitmap(bitmap);
            } else {
                holder.songImage.setImageResource(android.R.drawable.ic_media_play); // Placeholder
            }
        } else {
            // Fallback if not found in DB (e.g., new song not yet processed or metadata not available)
            // You might want to extract a display name from the identifier itself if it's a file path or URI
            holder.songTitle.setText(songIdentifier.substring(songIdentifier.lastIndexOf('/') + 1)); // Basic name from path/URI
            holder.songArtist.setText("Unknown Artist");
            holder.songImage.setImageResource(android.R.drawable.ic_media_play); // Placeholder
        }

        cursor.close();
        return convertView;
    }
}
