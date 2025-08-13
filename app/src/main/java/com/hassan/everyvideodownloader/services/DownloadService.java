package com.hassan.everyvideodownloader.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hassan.everyvideodownloader.R;
import com.hassan.everyvideodownloader.helpers.YtDlpHelper;
import com.hassan.everyvideodownloader.utils.Utils;

public class DownloadService extends Service {

    public static final String ACTION_START = "com.hassan.DOWNLOAD_START";
    public static final String ACTION_PROGRESS = "com.hassan.DOWNLOAD_PROGRESS";
    public static final String ACTION_COMPLETE = "com.hassan.DOWNLOAD_COMPLETE";
    public static final String ACTION_ERROR = "com.hassan.everyvideodownloader.ERROR";
    public static final String CHANNEL_ID = "download_channel";

    private final IBinder binder = new LocalBinder();

    // Keep last state for UI restore
    private int lastProgress = -1; // -1 = indeterminate
    private String lastStatus = null;

    public class LocalBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    private void sendErrorBroadcast(String message) {
        Intent intent = new Intent(ACTION_ERROR);
        intent.putExtra("error", message);
        sendBroadcast(intent);
    }


    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = intent.getStringExtra("url");
        Uri folderUri = Uri.parse(intent.getStringExtra("folderUri"));

        Log.d("DownloadService", "URL: " + url + ", Folder URI: " + folderUri);

        createNotificationChannel(); // ✅ Ensure channel exists first
        startForeground(1, buildNotification("Preparing download..."));

        YtDlpHelper ytDlpHelper = new YtDlpHelper(this);
        ytDlpHelper.downloadVideoWithFolder(url, folderUri, new YtDlpHelper.DownloadProgressCallback() {
            @Override
            public void onProgress(int percent, String statusText) {
                updateState(percent, statusText);
                updateNotification(percent, statusText);

                Intent progressIntent = new Intent(ACTION_PROGRESS);
                progressIntent.putExtra("percent", percent);
                progressIntent.putExtra("status", statusText);
                sendBroadcast(progressIntent);
            }

            @Override
            public void onComplete(String folderPath) {
                updateState(100, "Download finished");


                // Stop foreground but keep the notification temporarily
                stopForeground(false);

                // Build and show "Download complete" notification
                Notification completeNotification = new NotificationCompat.Builder(DownloadService.this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.evd_logo)
                        .setContentTitle("Download complete")
                        .setContentText("Video saved to: " + folderPath)
                        .setAutoCancel(true) // disappears when tapped
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build();

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(2, completeNotification); // Different ID so it doesn't overwrite progress notif
                }

                // Broadcast completion for UI
                Intent completeIntent = new Intent(ACTION_COMPLETE);
                sendBroadcast(completeIntent);

                stopSelf();
            }

            @Override
            public void onError(String error) {
                updateState(-1, error);
                updateNotification(-1, "Error: " + error);
                sendErrorBroadcast(error);

                stopForeground(true);
                stopSelf();
            }
        });

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Video download progress");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.evd_logo)
                .setContentTitle("Downloading video")
                .setContentText(content)
                .setProgress(0, 0, true) // indeterminate initially
                .setOngoing(true)
                .build();
    }

    private void updateNotification(int percent, String status) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.evd_logo)
                .setContentTitle("Downloading...");

        // ✅ If waiting or processing → show indeterminate progress
        if (("Waiting For Response...".equalsIgnoreCase(status) || "Processing...".equalsIgnoreCase(status) || "Making Video Upload Ready Wait...".equalsIgnoreCase(status))) {
            builder.setContentText(status) // just show the text
                    .setProgress(0, 0, true); // indeterminate
        }
        // ✅ Otherwise → show determinate progress with "XX% Downloaded"
        else if (percent >= 0) {
            builder.setContentText(percent + "% Downloaded")
                    .setProgress(100, percent, false);
        }
        // Fallback: if no percent, just show text
        else {
            builder.setContentText(status)
                    .setProgress(0, 0, true);
        }

        builder.setOngoing(true);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(1, builder.build());
        }
    }

    private void updateState(int progress, String status) {
        lastProgress = progress;
        lastStatus = status;
    }

    public int getLastProgress() {
        return lastProgress;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
