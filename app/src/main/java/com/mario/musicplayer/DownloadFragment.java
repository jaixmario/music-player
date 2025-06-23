package com.mario.musicplayer;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.IOException; // <--- ADDED THIS IMPORT

public class DownloadFragment extends Fragment {

    private EditText urlInput;
    private Button downloadButton;
    private ProgressBar progressBar;
    private Context context;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_download, container, false);

        context = requireContext();
        urlInput = view.findViewById(R.id.urlInput);
        downloadButton = view.findViewById(R.id.downloadButton);
        progressBar = view.findViewById(R.id.downloadProgress);

        progressBar.setVisibility(View.GONE);

        downloadButton.setOnClickListener(v -> {
            String ytUrl = urlInput.getText().toString().trim();
            if (ytUrl.isEmpty()) {
                Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show();
            } else {
                downloadMusicFromApi(ytUrl);
            }
        });

        return view;
    }

    private void downloadMusicFromApi(String ytUrl) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            OutputStream out = null;
            try {
                SharedPreferences prefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE);
                String baseUrl = prefs.getString("server_url", "https://f7bba52b-4af0-4efa-9b26-23a593b1826b-00-hxlccw5fjxp5.pike.replit.dev");
                String apiUrl = baseUrl + "/download?url=" + URLEncoder.encode(ytUrl, "UTF-8");
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    String tempFileName = "song_" + System.currentTimeMillis() + ".mp3";
                    String contentDisp = conn.getHeaderField("Content-Disposition");
                    if (contentDisp != null && contentDisp.contains("filename=")) {
                        int start = contentDisp.indexOf("filename=") + 9;
                        tempFileName = contentDisp.substring(start).replace("\"", "").trim();
                        tempFileName = tempFileName.replaceAll("[^a-zA-Z0-9\\.\\-]", "_"); // Sanitize filename
                    }
                    final String fileName = tempFileName;

                    Uri contentUri = null;
                    File outFile = null;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use MediaStore for Android 10 (API 29) and above
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg"); // Assuming MP3
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
                        contentUri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

                        if (contentUri == null) {
                            showError("Failed to create MediaStore entry.");
                            return;
                        }
                        out = context.getContentResolver().openOutputStream(contentUri);

                    } else {
                        // Use direct file path for Android 9 (API 28) and below
                        File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
                        if (!musicDir.exists()) {
                            boolean created = musicDir.mkdirs();
                            if (!created) {
                                showError("Failed to create Music directory.");
                                return;
                            }
                        }
                        outFile = new File(musicDir, fileName);
                        out = new FileOutputStream(outFile);
                    }

                    if (out == null) {
                        showError("Failed to open output stream.");
                        return;
                    }

                    in = new BufferedInputStream(conn.getInputStream());
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }

                    out.flush();

                    final Uri finalContentUri = contentUri;
                    final File finalOutFile = outFile;

                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(context, "Saved as " + fileName + " in Music folder", Toast.LENGTH_LONG).show();

                        // Trigger MediaScanner to make the file visible
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            // For older Android versions, use MediaScannerConnection
                            MediaScannerConnection.scanFile(
                                    context,
                                    new String[]{finalOutFile.getAbsolutePath()},
                                    null,
                                    (path, uri) -> requireActivity().runOnUiThread(() -> {
                                        Toast.makeText(context, "New song added to library", Toast.LENGTH_SHORT).show();
                                        context.sendBroadcast(new Intent("SONG_ADDED"));
                                    }));
                        } else {
                            // For Android 10+, MediaStore insertion usually handles scanning automatically.
                            // Just send the broadcast to refresh the UI.
                            Toast.makeText(context, "New song added to library", Toast.LENGTH_SHORT).show();
                            context.sendBroadcast(new Intent("SONG_ADDED"));
                        }
                    });
                } else {
                    showError("Failed to download (Response code: " + conn.getResponseCode() + ")");
                }

            } catch (Exception e) { // Catching generic Exception to include IOException
                showError("Error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (conn != null) conn.disconnect();
                } catch (IOException e) { // This IOException is now recognized
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showError(String msg) {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        });
    }
}
