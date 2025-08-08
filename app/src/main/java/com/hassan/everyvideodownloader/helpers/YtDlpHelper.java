package com.hassan.everyvideodownloader.helpers;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;

public class YtDlpHelper {
    private static final String TAG = "YtDlpHelper";
    private final Context context;

    public YtDlpHelper(Context context) {
        this.context = context;
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
    }

    public void downloadVideo(String url, DownloadCallback callback) {
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject downloader = py.getModule("downloader");

                // Only pass URL — no ffmpeg path required now
                PyObject result = downloader.callAttr("download_video", url);

                if (result != null && !result.toString().startsWith("❌")) {
                    String folderPath = result.toString();
                    File folder = new File(folderPath);

                    if (folder.exists() && folder.isDirectory()) {
                        for (File file : folder.listFiles()) {
                            if (file.isFile()) {
                                MediaScannerConnection.scanFile(
                                        context,
                                        new String[]{file.getAbsolutePath()},
                                        null,
                                        null
                                );
                            }
                        }
                    }

                    callback.onSuccess("✅ Downloaded to: " + folderPath);
                } else {
                    callback.onError(result != null ? result.toString() : "❌ Unknown error");
                }
            } catch (Exception e) {
                Log.e(TAG, "Python call failed", e);
                callback.onError("❌ Error: " + e.getMessage());
            }
        }).start();
    }

    public interface DownloadCallback {
        void onSuccess(String message);
        void onError(String error);
    }
}
