package com.mario.musicplayer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

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
            try {
                SharedPreferences prefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE);
                String baseUrl = prefs.getString("server_url", "https://f7bba52b-4af0-4efa-9b26-23a593b1826b-00-hxlccw5fjxp5.pike.replit.dev");
                String apiUrl = baseUrl + "/download?url=" + URLEncoder.encode(ytUrl, "UTF-8");
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    File musicDir = new File(Environment.getExternalStorageDirectory(), "Music");
                    if (!musicDir.exists()) musicDir.mkdirs();

                    String tempFileName = "song_" + System.currentTimeMillis() + ".mp3";
                    String contentDisp = conn.getHeaderField("Content-Disposition");
                    if (contentDisp != null && contentDisp.contains("filename=")) {
                        int start = contentDisp.indexOf("filename=") + 9;
                        tempFileName = contentDisp.substring(start).replace("\"", "").trim();
                    }

                    final String fileName = tempFileName;
                    File outFile = new File(musicDir, fileName);

                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    FileOutputStream out = new FileOutputStream(outFile);

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }

                    out.flush();
                    out.close();
                    in.close();

                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(context, "Saved as " + fileName + " in /Music", Toast.LENGTH_LONG).show();

                        // Scan and notify
                        MediaScannerConnection.scanFile(
                                context,
                                new String[]{outFile.getAbsolutePath()},
                                null,
                                (path, uri) -> requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(context, "New song added to library", Toast.LENGTH_SHORT).show();
                                    context.sendBroadcast(new Intent("SONG_ADDED"));
                                }));
                    });
                } else {
                    showError("Failed to download (Response code: " + conn.getResponseCode() + ")");
                }

                conn.disconnect();
            } catch (Exception e) {
                showError("Error: " + e.getMessage());
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