package com.mario.musicplayer;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.View;
import android.view.animation.*;
import android.widget.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.palette.graphics.Palette;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.imageview.ShapeableImageView;
import android.provider.MediaStore;
import android.content.ContentUris;
import androidx.annotation.Nullable; // <--- ADD THIS IMPORT

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ListView listView;
    private ImageButton playPauseButton, nextButton, prevButton;
    private ShapeableImageView albumArt;
    private ImageView blurGlow;
    private TextView titleText, artistText, currentTimeText, durationText;
    private SeekBar seekBar, miniSeekBar;

    private LinearLayout fullPlayerLayout, miniPlayer;
    private ImageView miniAlbumArt;
    private TextView miniTitle, miniArtist;
    private ImageButton miniPlayPause;

    private ProgressBar loadingProgressBar;
    private TextView loadingStatusText;

    private ArrayList<String> songList;
    private int currentSongIndex = -1;
    private final int REQUEST_POST_NOTIFICATIONS = 1002;
    private Handler handler = new Handler();
    private Animation rotateAnim;
    private SharedPreferences prefs;

    private DatabaseHelper db;
    private ExecutorService executorService;

    private ActivityResultLauncher<Uri> openDocumentTreeLauncher;
    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            String songIdentifier = intent.getStringExtra("song_identifier");

            if ("paused".equals(status)) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                miniPlayPause.setImageResource(android.R.drawable.ic_media_play);
                albumArt.clearAnimation();
            } else if ("resumed".equals(status) || "started".equals(status)) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                miniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                albumArt.startAnimation(rotateAnim);
                startSeekBarUpdate();
            } else if ("next".equals(status) && songIdentifier != null) {
                currentSongIndex = findIndexByIdentifier(songIdentifier);
                updateMetadataUI(songIdentifier);
                updateMiniPlayerUI();
            } else if ("stopped".equals(status)) {
                miniPlayer.setVisibility(View.GONE);
                fullPlayerLayout.setVisibility(View.GONE);
                albumArt.clearAnimation();
            }
        }
    };

    private final BroadcastReceiver songAddedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkAndLoadSongs();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new CrashLogger(this));
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        prefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE);
        executorService = Executors.newSingleThreadExecutor();

        fullPlayerLayout = findViewById(R.id.fullPlayerLayout);
        miniPlayer = findViewById(R.id.miniPlayer);
        miniAlbumArt = findViewById(R.id.miniAlbumArt);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlayPause = findViewById(R.id.miniPlayPause);
        miniSeekBar = findViewById(R.id.miniSeekBar);
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

        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        loadingStatusText = findViewById(R.id.loadingStatusText);

        openDocumentTreeLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                prefs.edit().putString("music_folder_uri", uri.toString()).apply();
                loadSongsInBackground(uri);
            } else {
                Toast.makeText(this, "Music folder selection cancelled. Cannot load songs.", Toast.LENGTH_LONG).show();
                hideLoadingIndicators();
            }
        });

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
            boolean allGranted = true;
            for (Boolean granted : permissions.values()) {
                if (!granted) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                checkAndLoadSongs();
            } else {
                Toast.makeText(this, "Permissions denied. Cannot load songs.", Toast.LENGTH_LONG).show();
                hideLoadingIndicators();
            }
        });

        miniSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MediaPlayer player = MusicService.getMediaPlayer();
                    if (player != null) {
                        int duration = player.getDuration();
                        int newPos = (duration * progress) / 100;
                        player.seekTo(newPos);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
            }
        }

        checkAndLoadSongs();

        BottomNavigationView navView = findViewById(R.id.bottomNavigationView);
        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                findViewById(R.id.fragment_container).setVisibility(View.GONE);
                findViewById(R.id.mainContentArea).setVisibility(View.VISIBLE);

                boolean isFullPlayerVisible = prefs.getBoolean("is_full_player_visible", false);
                MediaPlayer player = MusicService.getMediaPlayer();
                boolean isPlaying = player != null && player.isPlaying();

                if (isFullPlayerVisible) {
                    fullPlayerLayout.setVisibility(View.VISIBLE);
                    miniPlayer.setVisibility(View.GONE);
                } else if (isPlaying) {
                    fullPlayerLayout.setVisibility(View.GONE);
                    miniPlayer.setVisibility(View.VISIBLE);
                } else {
                    fullPlayerLayout.setVisibility(View.GONE);
                    miniPlayer.setVisibility(View.GONE);
                }

                return true;
            } else if (id == R.id.nav_download) {
                findViewById(R.id.mainContentArea).setVisibility(View.GONE);
                fullPlayerLayout.setVisibility(View.GONE);
                miniPlayer.setVisibility(View.GONE);
                findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);

                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DownloadFragment())
                    .addToBackStack(null)
                    .commit();

                return true;
            } else if (id == R.id.nav_settings) {
                findViewById(R.id.mainContentArea).setVisibility(View.GONE);
                fullPlayerLayout.setVisibility(View.GONE);
                miniPlayer.setVisibility(View.GONE);
                findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);

                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new SettingsFragment())
                    .addToBackStack(null)
                    .commit();

                return true;
            }
            return false;
        });
    }

    private void showLoadingIndicators() {
        listView.setVisibility(View.GONE);
        loadingProgressBar.setVisibility(View.VISIBLE);
        loadingStatusText.setVisibility(View.VISIBLE);
    }

    private void hideLoadingIndicators() {
        loadingProgressBar.setVisibility(View.GONE);
        loadingStatusText.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);
    }

    private void checkAndLoadSongs() {
        showLoadingIndicators();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_AUDIO});
            } else {
                loadSongsInBackground(null);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String storedUriString = prefs.getString("music_folder_uri", null);
            if (storedUriString != null) {
                Uri storedUri = Uri.parse(storedUriString);
                if (checkUriPermission(storedUri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                    loadSongsInBackground(storedUri);
                } else {
                    showSafPermissionDialog();
                }
            } else {
                showSafPermissionDialog();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            } else {
                loadSongsInBackground(null);
            }
        }
    }

    private void showSafPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Select Music Folder")
                .setMessage("To play music, please select your music folder (e.g., 'Music' or 'Download') using the 'Use this folder' option in the next screen.")
                .setCancelable(false)
                .setPositiveButton("Select Folder", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    openDocumentTreeLauncher.launch(null);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Music folder selection is required to load songs.", Toast.LENGTH_LONG).show();
                    hideLoadingIndicators();
                })
                .show();
    }

    private void loadSongsInBackground(@Nullable Uri safTreeUri) {
        executorService.execute(() -> {
            ArrayList<String> newSongList = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                newSongList = getSongsFromMediaStore();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && safTreeUri != null) {
                newSongList = getSongsFromSaf(safTreeUri);
            } else {
                newSongList = getSongsFromLegacyPath();
            }

            processAndSaveSongMetadata(newSongList);

            runOnUiThread(() -> {
                songList = newSongList;
                SongAdapter adapter = new SongAdapter(MainActivity.this, songList);
                listView.setAdapter(adapter);
                hideLoadingIndicators();
                Toast.makeText(MainActivity.this, "Music loaded!", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private ArrayList<String> getSongsFromLegacyPath() {
        ArrayList<String> songs = new ArrayList<>();
        File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
        findSongsInFile(musicDir, songs);
        return songs;
    }

    private ArrayList<String> getSongsFromSaf(Uri treeUri) {
        ArrayList<String> songs = new ArrayList<>();
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
        if (pickedDir != null && pickedDir.isDirectory()) {
            findSongsInDocumentFile(pickedDir, songs);
        }
        return songs;
    }

    private ArrayList<String> getSongsFromMediaStore() {
        ArrayList<String> songs = new ArrayList<>();
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
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
                    songs.add(contentUri.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }

    private void processAndSaveSongMetadata(ArrayList<String> songsToProcess) {
        ArrayList<DatabaseHelper.SongMetadata> metadataList = new ArrayList<>();
        for (String songIdentifier : songsToProcess) {
            if (!db.isSongCached(songIdentifier)) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    if (songIdentifier.startsWith("content://")) {
                        retriever.setDataSource(this, Uri.parse(songIdentifier));
                    } else {
                        retriever.setDataSource(songIdentifier);
                    }

                    String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    byte[] art = retriever.getEmbeddedPicture();

                    int duration = (durationStr != null) ? Integer.parseInt(durationStr) : 0;

                    metadataList.add(new DatabaseHelper.SongMetadata(
                            songIdentifier,
                            title != null ? title : "Unknown Title",
                            artist != null ? artist : "Unknown Artist",
                            duration,
                            art
                    ));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        retriever.release();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (!metadataList.isEmpty()) {
            db.insertSongsBatch(metadataList);
        }
    }

    private void findSongsInFile(File dir, ArrayList<String> songs) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) findSongsInFile(f, songs);
                else if (f.getName().endsWith(".mp3") || f.getName().endsWith(".m4a")) songs.add(f.getAbsolutePath());
            }
        }
    }

    private void findSongsInDocumentFile(DocumentFile dir, ArrayList<String> songs) {
        for (DocumentFile file : dir.listFiles()) {
            if (file.isDirectory()) {
                findSongsInDocumentFile(file, songs);
            } else if (file.isFile() && (file.getName().endsWith(".mp3") || file.getName().endsWith(".m4a"))) {
                songs.add(file.getUri().toString());
            }
        }
    }

    private void playCurrentSong() {
        String songIdentifier = songList.get(currentSongIndex);
        updateMetadataUI(songIdentifier);
        prefs.edit().putInt("last_index", currentSongIndex).apply();

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_START);
        intent.putExtra("song_identifier", songIdentifier);
        startService(intent);

        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        miniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        albumArt.startAnimation(rotateAnim);
        startSeekBarUpdate();

        updateMiniPlayerUI();
        miniPlayer.setVisibility(View.VISIBLE);
        showFullPlayer();
    }

    private void updateMetadataUI(String songIdentifier) {
        Cursor cursor = db.getSong(songIdentifier);
        if (cursor.moveToFirst()) {
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            String artist = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ARTIST));
            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DURATION));
            byte[] art = cursor.getBlob(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ART));

            titleText.setText(title);
            artistText.setText(artist);
            seekBar.setMax(duration);
            durationText.setText(millisecondsToTimer(duration));

            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                albumArt.setImageBitmap(bitmap);
                applyDynamicBlur(bitmap);
            } else {
                albumArt.setImageResource(android.R.drawable.ic_media_play);
            }
        }
        cursor.close();
    }

    private void updateMiniPlayerUI() {
        if (songList == null || currentSongIndex < 0 || currentSongIndex >= songList.size()) return;

        String songIdentifier = songList.get(currentSongIndex);
        Cursor cursor = db.getSong(songIdentifier);
        if (cursor.moveToFirst()) {
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            String artist = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ARTIST));
            byte[] art = cursor.getBlob(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ART));

            miniTitle.setText(title);
            miniArtist.setText(artist);
            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                miniAlbumArt.setImageBitmap(bitmap);
            } else {
                miniAlbumArt.setImageResource(android.R.drawable.ic_media_play);
            }
        }
        cursor.close();
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

    private void startSeekBarUpdate() {
        handler.postDelayed(new Runnable() {
            public void run() {
                MediaPlayer player = MusicService.getMediaPlayer();
                if (player != null && player.isPlaying()) {
                    int currentPos = player.getCurrentPosition();
                    seekBar.setProgress(currentPos);
                    int duration = player.getDuration();
                    int progress = (int) ((currentPos / (float) duration) * 100);
                    miniSeekBar.setProgress(progress);
                    currentTimeText.setText(millisecondsToTimer(currentPos));
                }
                handler.postDelayed(this, 500);
            }
        }, 0);
    }

    private String millisecondsToTimer(int ms) {
        int mins = (ms / 1000) / 60;
        int secs = (ms / 1000) % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private void showFullPlayer() {
        fullPlayerLayout.setVisibility(View.VISIBLE);
        miniPlayer.setVisibility(View.GONE);
        prefs.edit().putBoolean("is_full_player_visible", true).apply();
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

    private int findIndexByIdentifier(String identifier) {
        if (songList == null) return -1;
        for (int i = 0; i < songList.size(); i++) {
            if (songList.get(i).equals(identifier)) {
                return i;
            }
        }
        return -1;
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

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(updateReceiver, new IntentFilter("UPDATE_UI"));
        registerReceiver(songAddedReceiver, new IntentFilter("SONG_ADDED"));

        currentSongIndex = prefs.getInt("last_index", -1);
        boolean wasFullPlayerVisible = prefs.getBoolean("is_full_player_visible", false);

        MediaPlayer player = MusicService.getMediaPlayer();
        String currentSongIdentifier = MusicService.getCurrentSongIdentifier();

        if (player != null && player.isPlaying() && currentSongIdentifier != null
                && songList != null && currentSongIndex >= 0 && currentSongIndex < songList.size()) {

            updateMetadataUI(currentSongIdentifier);
            updateMiniPlayerUI();

            int duration = player.getDuration();
            seekBar.setMax(duration);
            durationText.setText(millisecondsToTimer(duration));

            int currentPos = player.getCurrentPosition();
            seekBar.setProgress(currentPos);
            currentTimeText.setText(millisecondsToTimer(currentPos));

            int progress = (int) ((currentPos / (float) duration) * 100);
            miniSeekBar.setProgress(progress);

            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            miniPlayPause.setImageResource(android.R.drawable.ic_media_pause);

            albumArt.startAnimation(rotateAnim);
            startSeekBarUpdate();

            if (wasFullPlayerVisible) {
                fullPlayerLayout.setVisibility(View.VISIBLE);
                miniPlayer.setVisibility(View.GONE);
            } else {
                fullPlayerLayout.setVisibility(View.GONE);
                miniPlayer.setVisibility(View.VISIBLE);
            }
        } else {
            miniPlayer.setVisibility(View.GONE);
            fullPlayerLayout.setVisibility(View.GONE);
            seekBar.setProgress(0);
            miniSeekBar.setProgress(0);
            currentTimeText.setText("0:00");
            durationText.setText("0:00");
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            miniPlayPause.setImageResource(android.R.drawable.ic_media_play);
            albumArt.clearAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateReceiver);
        unregisterReceiver(songAddedReceiver);
    }

    @Override
    public void onBackPressed() {
        if (fullPlayerLayout.getVisibility() == View.VISIBLE) {
            fullPlayerLayout.setVisibility(View.GONE);
            miniPlayer.setVisibility(View.VISIBLE);
            prefs.edit().putBoolean("is_full_player_visible", false).apply();
        } else if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) instanceof SettingsFragment) {
            return;
        } else {
            super.onBackPressed();
        }
    }
}
