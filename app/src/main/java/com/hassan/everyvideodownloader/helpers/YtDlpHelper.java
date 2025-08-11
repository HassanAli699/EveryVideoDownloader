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

    public void downloadVideo(String url, DownloadProgressCallback callback) {
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject downloader = py.getModule("downloader");

                // Pass Java callback object to Python directly
                downloader.callAttr("download_video_with_progress", url, new ProgressBridge(callback));

            } catch (Exception e) {
                Log.e(TAG, "Python call failed", e);
                callback.onError("❌ Error: " + e.getMessage());
            }
        }).start();
    }

    public interface DownloadProgressCallback {
        void onProgress(int percent, String statusText);
        void onComplete(String folderPath);
        void onError(String error);
    }

    // Plain Java bridge class
    public static class ProgressBridge {
        private final DownloadProgressCallback callback;

        public ProgressBridge(DownloadProgressCallback callback) {
            this.callback = callback;
        }

        public void update_progress(int percent, String status) {
            callback.onProgress(percent, status);
        }

        public void completed(String folderPath) {
            callback.onComplete(folderPath);
        }

        public void error(String errorMsg) {
            callback.onError(errorMsg);
        }
    }
}
