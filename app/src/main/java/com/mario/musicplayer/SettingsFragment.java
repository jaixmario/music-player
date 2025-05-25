package com.mario.musicplayer;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.*;
import android.os.Environment;
import android.view.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.*;

public class SettingsFragment extends Fragment {

    private static final int REQUEST_PERMISSION = 123;
    private TextInputEditText serverUrlInput;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = requireContext().getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE);

        serverUrlInput = view.findViewById(R.id.serverUrlInput);
        MaterialButton saveUrlButton = view.findViewById(R.id.saveUrlButton);
        MaterialButton btnExportDb = view.findViewById(R.id.btnExportDb);

        // Load and set saved URL
        String savedUrl = prefs.getString("server_url", "https://default.server.com");
        serverUrlInput.setText(savedUrl);

        // Save URL
        saveUrlButton.setOnClickListener(v -> {
            String newUrl = serverUrlInput.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                prefs.edit().putString("server_url", newUrl).apply();
                Toast.makeText(requireContext(), "URL saved!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Please enter a valid URL", Toast.LENGTH_SHORT).show();
            }
        });

        // Export DB button
        btnExportDb.setOnClickListener(v -> exportDatabase());

        // Request storage permission if needed (for Android 10 and below)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }
    }

    private void exportDatabase() {
        try {
            File dbFile = requireContext().getDatabasePath("music_meta.db");
            File exportDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "MusicPlayerBackup");

            if (!exportDir.exists()) exportDir.mkdirs();

            File outFile = new File(exportDir, "music_meta_backup.db");

            try (InputStream in = new FileInputStream(dbFile);
                 OutputStream out = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[1024];
                int length;

                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }

            Toast.makeText(requireContext(), "Exported to:\n" + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}