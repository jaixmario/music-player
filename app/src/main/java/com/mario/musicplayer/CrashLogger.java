package com.mario.musicplayer;

import android.content.*;
import android.os.*;
import java.io.*;

public class CrashLogger implements Thread.UncaughtExceptionHandler {

    private final Context context;

    public CrashLogger(Context ctx) {
        this.context = ctx;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            File logFile = new File(context.getExternalFilesDir(null), "crash_log.txt");
            PrintWriter writer = new PrintWriter(new FileWriter(logFile, true));
            writer.println("=== Crash at " + System.currentTimeMillis() + " ===");
            throwable.printStackTrace(writer);
            writer.println();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Optional: wait & kill app to prevent looping
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}