package com.mario.musicplayer;

import android.Manifest;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.View;
import android.view.animation.*;
import android.widget.*;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ListView listView;
    private ImageButton playPauseButton, nextButton, prevButton;
    private ShapeableImageView albumArt;
    private ImageView blurGlow;
    private TextView titleText, artistText, currentTimeText, durationText;
    private SeekBar seekBar;

    private LinearLayout fullPlayerLayout, miniPlayer;
    private ImageView miniAlbumArt;
    private TextView miniTitle, miniArtist;
    private ImageButton miniPlayPause;

    private ArrayList<File> songList;
    private int currentSongIndex = -1;
    private final int REQUEST_PERMISSION = 1001;
    private Handler handler = new Handler();
    private Animation rotateAnim;
    private SharedPreferences prefs;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            String path = intent.getStringExtra("song_path");

            if ("paused".equals(status)) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                miniPlayPause.setImageResource(android.R.drawable.ic_media_play);
                albumArt.clearAnimation();

            } else if ("resumed".equals(status) || "started".equals(status)) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                miniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                albumArt.startAnimation(rotateAnim);
                startSeekBarUpdate();

            } else if ("next".equals(status) && path != null) {
                currentSongIndex = findIndexByPath(path); // fix: update index
                extractMetadata(path);
                updateMiniPlayerUI();

            } else if ("stopped".equals(status)) {
                miniPlayer.setVisibility(View.GONE);
                fullPlayerLayout.setVisibility(View.GONE);
                albumArt.clearAnimation();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE);

        fullPlayerLayout = findViewById(R.id.fullPlayerLayout);
        miniPlayer = findViewById(R.id.miniPlayer);
        miniAlbumArt = findViewById(R.id.miniAlbumArt);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlayPause = findViewById(R.id.miniPlayPause);

        listView = findViewById(R.id.listView);
        playPauseButton = findViewById(R.id.playPauseButton);
        nextButton = findViewById(R.id.nextButton);
        prevButton = findViewById(R.id.prevButton);
        albumArt = findViewById(R.id.albumArt);
        blurGlow = findViewById(R.id.blurGlow);
        titleText = findViewById(R.id.titleText);
        artistText = findViewById(R.id.artistText);
        currentTimeText = findViewById(R.id.currentTime);
        durationText = findViewById(R.id.totalTime);
        seekBar = findViewById(R.id.seekBar);
        rotateAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_album);

        playPauseButton.setOnClickListener(v -> togglePlayPause());
        nextButton.setOnClickListener(v -> playNext());
        prevButton.setOnClickListener(v -> playPrevious());
        miniPlayPause.setOnClickListener(v -> togglePlayPause());
        miniPlayer.setOnClickListener(v -> showFullPlayer());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    MediaPlayer player = MusicService.getMediaPlayer();
                    if (player != null) player.seekTo(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        miniPlayer.setVisibility(View.GONE);
        fullPlayerLayout.setVisibility(View.GONE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        } else {
            loadSongs();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(updateReceiver, new IntentFilter("UPDATE_UI"));

        if (songList == null || songList.isEmpty()) {
            loadSongs();
        }

        currentSongIndex = prefs.getInt("last_index", -1);

        MediaPlayer player = MusicService.getMediaPlayer();
        String currentPath = MusicService.getCurrentPath();

        if (player != null && currentPath != null
                && currentSongIndex >= 0 && currentSongIndex < songList.size()) {

            extractMetadata(currentPath);
            updateMiniPlayerUI();

            int duration = player.getDuration();
            seekBar.setMax(duration);
            durationText.setText(millisecondsToTimer(duration));

            int currentPos = player.getCurrentPosition();
            seekBar.setProgress(currentPos);
            currentTimeText.setText(millisecondsToTimer(currentPos));

            playPauseButton.setImageResource(player.isPlaying()
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);
            miniPlayPause.setImageResource(player.isPlaying()
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);

            if (player.isPlaying()) {
                albumArt.startAnimation(rotateAnim);
                startSeekBarUpdate();
            } else {
                albumArt.clearAnimation();
            }

            miniPlayer.setVisibility(View.VISIBLE);
        } else {
            miniPlayer.setVisibility(View.GONE);
            fullPlayerLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateReceiver);
    }

    @Override
    public void onBackPressed() {
        if (fullPlayerLayout.getVisibility() == View.VISIBLE) {
            fullPlayerLayout.setVisibility(View.GONE);
            miniPlayer.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }

    private void togglePlayPause() {
        MediaPlayer player = MusicService.getMediaPlayer();
        if (player != null) {
            if (player.isPlaying()) {
                sendActionToService(MusicService.ACTION_PAUSE);
            } else {
                sendActionToService(MusicService.ACTION_RESUME);
            }
        }
    }

    private void sendActionToService(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void loadSongs() {
        File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
        songList = findSongs(musicDir);
        ArrayList<String> names = new ArrayList<>();
        for (File f : songList) names.add(f.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, pos, id) -> {
            currentSongIndex = pos;
            playCurrentSong();
        });
    }

    private void playCurrentSong() {
        File song = songList.get(currentSongIndex);
        extractMetadata(song.getAbsolutePath());
        prefs.edit().putInt("last_index", currentSongIndex).apply();

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_START);
        intent.putExtra("song_path", song.getAbsolutePath());
        startService(intent);

        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        miniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        albumArt.startAnimation(rotateAnim);
        startSeekBarUpdate();

        updateMiniPlayerUI();
        miniPlayer.setVisibility(View.VISIBLE);
        showFullPlayer();
    }

    private void playNext() {
        if (songList == null || songList.isEmpty()) return;
        currentSongIndex = (currentSongIndex + 1) % songList.size();
        playCurrentSong();
    }

    private void playPrevious() {
        if (songList == null || songList.isEmpty()) return;
        currentSongIndex = (currentSongIndex - 1 + songList.size()) % songList.size();
        playCurrentSong();
    }

    private void startSeekBarUpdate() {
        handler.postDelayed(new Runnable() {
            public void run() {
                MediaPlayer player = MusicService.getMediaPlayer();
                if (player != null && player.isPlaying()) {
                    int currentPos = player.getCurrentPosition();
                    seekBar.setProgress(currentPos);
                    currentTimeText.setText(millisecondsToTimer(currentPos));
                }
                handler.postDelayed(this, 500);
            }
        }, 0);
    }

    private void extractMetadata(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            byte[] art = retriever.getEmbeddedPicture();

            titleText.setText(title != null ? title : "Unknown Title");
            artistText.setText(artist != null ? artist : "Unknown Artist");

            if (duration != null) {
                int ms = Integer.parseInt(duration);
                seekBar.setMax(ms);
                durationText.setText(millisecondsToTimer(ms));
            }

            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                albumArt.setImageBitmap(bitmap);
                applyDynamicBlur(bitmap);
            } else {
                albumArt.setImageResource(android.R.drawable.ic_media_play);
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
    }

    private void updateMiniPlayerUI() {
        if (songList == null || currentSongIndex < 0 || currentSongIndex >= songList.size()) return;

        File song = songList.get(currentSongIndex);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(song.getAbsolutePath());

        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        byte[] art = retriever.getEmbeddedPicture();

        miniTitle.setText(title != null ? title : "Unknown Title");
        miniArtist.setText(artist != null ? artist : "Unknown Artist");

        if (art != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
            miniAlbumArt.setImageBitmap(bitmap);
        } else {
            miniAlbumArt.setImageResource(android.R.drawable.ic_media_play);
        }

        try {
            retriever.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyDynamicBlur(Bitmap albumBitmap) {
        if (albumBitmap == null) return;

        Palette.from(albumBitmap).generate(palette -> {
            int dominantColor = palette != null ? palette.getDominantColor(Color.WHITE) : Color.WHITE;

            Bitmap blurBitmap = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(blurBitmap);

            Paint colorPaint = new Paint();
            colorPaint.setColor(dominantColor);
            colorPaint.setAntiAlias(true);
            colorPaint.setMaskFilter(new BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawCircle(90f, 90f, 60f, colorPaint);

            Paint whitePaint = new Paint();
            whitePaint.setColor(Color.WHITE);
            whitePaint.setAlpha(60);
            whitePaint.setAntiAlias(true);
            whitePaint.setMaskFilter(new BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawCircle(90f, 90f, 65f, whitePaint);

            blurGlow.setImageBitmap(blurBitmap);
        });
    }

    private String millisecondsToTimer(int ms) {
        int mins = (ms / 1000) / 60;
        int secs = (ms / 1000) % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private ArrayList<File> findSongs(File dir) {
        ArrayList<File> songs = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) songs.addAll(findSongs(f));
                else if (f.getName().endsWith(".mp3") || f.getName().endsWith(".m4a")) songs.add(f);
            }
        }
        return songs;
    }

    private void showFullPlayer() {
        fullPlayerLayout.setVisibility(View.VISIBLE);
        miniPlayer.setVisibility(View.GONE);
    }

    private int findIndexByPath(String path) {
        if (songList == null) return -1;
        for (int i = 0; i < songList.size(); i++) {
            if (songList.get(i).getAbsolutePath().equals(path)) {
                return i;
            }
        }
        return -1;
    }
}