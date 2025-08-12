package com.hassan.everyvideodownloader.helpers;


import static android.app.PendingIntent.getActivity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.hassan.everyvideodownloader.R;
import com.hassan.everyvideodownloader.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class YtDlpHelper {
    private static final String TAG = "YtDlpHelper";
    private final Context context;

    public YtDlpHelper(Context context) {
        this.context = context;
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
    }


    public void downloadVideoWithFolder(String url, Uri folderUri, DownloadProgressCallback callback) {
        new Thread(() -> {
            try {
                context.getContentResolver().takePersistableUriPermission(
                        folderUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );

                File tempFolder = new File(context.getCacheDir(), "download_tmp");
                if (tempFolder.exists()) {
                    deleteRecursive(tempFolder);
                }
                tempFolder.mkdirs();

                Python py = Python.getInstance();
                PyObject downloader = py.getModule("downloader");

                downloader.callAttr("download_video_with_progress",
                        url,
                        tempFolder.getAbsolutePath(),
                        new ProgressBridge(new DownloadProgressCallback() {
                            @Override
                            public void onProgress(int percent, String statusText) {
                                callback.onProgress(percent, statusText);
                            }

                            @Override
                            public void onComplete(String mergedFilePath) {
                                try {
                                    File mergedFile = new File(mergedFilePath);
                                    DocumentFile pickedFolder = DocumentFile.fromTreeUri(context, folderUri);
                                    if (pickedFolder == null || !pickedFolder.canWrite()) {
                                        callback.onError("❌ Selected folder is not writable");
                                        return;
                                    }

                                    DocumentFile newFile = pickedFolder.createFile("video/mp4", mergedFile.getName());
                                    if (newFile == null) {
                                        callback.onError("❌ Failed to create file in folder");
                                        return;
                                    }

                                    try (InputStream in = new FileInputStream(mergedFile);
                                         OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {
                                        byte[] buffer = new byte[8192];
                                        int len;
                                        while ((len = in.read(buffer)) != -1) {
                                            out.write(buffer, 0, len);
                                        }
                                    }

                                    // Delete temp folder
                                    deleteRecursive(tempFolder);


                                    callback.onComplete(newFile.getUri().toString());

                                } catch (Exception e) {
                                    callback.onError("❌ Error moving file: " + e.getMessage());
                                }
                            }

                            @Override
                            public void onError(String error) {
                                callback.onError(error);
                            }
                        })
                );

            } catch (Exception e) {
                callback.onError("❌ Error: " + e.getMessage());
            }
        }).start();
    }


    // Helper: safely delete directories if needed
    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            for (File child : fileOrDir.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDir.delete();
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
