package com.mario.musicplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.*;
import android.os.Environment;
import android.view.*;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.*;

public class SettingsFragment extends Fragment {

    private static final int REQUEST_PERMISSION = 123;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button btnExportDb = view.findViewById(R.id.btnExportDb);
        BottomNavigationView navView = view.findViewById(R.id.bottomNavigationView);

        navView.setSelectedItemId(R.id.nav_settings);

        btnExportDb.setOnClickListener(v -> exportDatabase());

        navView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                requireActivity().getSupportFragmentManager().popBackStack();
                return true;
            } else if (item.getItemId() == R.id.nav_download) {
                Toast.makeText(requireContext(), "Download feature coming soon!", Toast.LENGTH_SHORT).show();
                return true;
            } else if (item.getItemId() == R.id.nav_settings) {
                return true;
            }
            return false;
        });

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