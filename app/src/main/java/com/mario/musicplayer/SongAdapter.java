package com.mario.musicplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.view.*;
import android.widget.*;

import java.io.File;
import java.util.ArrayList;

public class SongAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<File> songs;
    private LayoutInflater inflater;

    public SongAdapter(Context context, ArrayList<File> songs) {
        this.context = context;
        this.songs = songs;
        inflater = LayoutInflater.from(context);
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
        File song = songs.get(position);

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

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(song.getAbsolutePath());
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            byte[] art = retriever.getEmbeddedPicture();

            holder.songTitle.setText(title != null ? title : "Unknown Title");
            holder.songArtist.setText(artist != null ? artist : "Unknown Artist");

            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                holder.songImage.setImageBitmap(bitmap);
            } else {
                holder.songImage.setImageResource(android.R.drawable.ic_media_play);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return convertView;
    }
}