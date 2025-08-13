package com.hassan.everyvideodownloader.helpers;


import static android.app.PendingIntent.getActivity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFprobeKit;
import com.antonkarpenko.ffmpegkit.MediaInformationSession;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

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
                                    convertToTikTokFormatAndSave(mergedFile, folderUri, callback);
                                } catch (Exception e) {
                                    callback.onError("❌ Error converting to TikTok format: " + e.getMessage());
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

    private void convertToTikTokFormatAndSave(File inputFile, Uri folderUri, DownloadProgressCallback callback) {
        try {
            DocumentFile pickedFolder = DocumentFile.fromTreeUri(context, folderUri);
            if (pickedFolder == null || !pickedFolder.canWrite()) {
                callback.onError("❌ Selected folder is not writable");
                return;
            }

            String baseName = inputFile.getName();
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf("."));
            }
            String outputName = baseName + "_tiktok.mp4";

            DocumentFile outFileDoc = pickedFolder.createFile("video/mp4", outputName);
            if (outFileDoc == null) {
                callback.onError("❌ Failed to create TikTok file");
                return;
            }

            File tempOutFile = new File(context.getCacheDir(), outputName);

            // Step 1: Get media info
            MediaInformationSession infoSession = FFprobeKit.getMediaInformation(inputFile.getAbsolutePath());
            String videoCodec = infoSession.getMediaInformation().getStreams().get(0).getCodec();
            String audioCodec = infoSession.getMediaInformation().getStreams().size() > 1 ?
                    infoSession.getMediaInformation().getStreams().get(1).getCodec() : "";

            boolean needsReencode = false;

            // TikTok wants h264 video + aac audio
            if (!videoCodec.equalsIgnoreCase("h264") || !audioCodec.equalsIgnoreCase("aac")) {
                needsReencode = true;
            }

            String cmd;
            String format = String.format(
                    "-i \"%s\" -c:v libx264 -preset fast -crf 18 -profile:v high -level 4.1 " +
                            "-c:a aac -b:a 128k -ar 44100 -movflags +faststart -fps_mode passthrough \"%s\"",
                    inputFile.getAbsolutePath(),
                    tempOutFile.getAbsolutePath()
            );
//            if (!needsReencode) {
//                cmd = format;
//            } else {
//                cmd = format;
//                Log.d("ReCODING", "RECONDING");
//            }

            com.antonkarpenko.ffmpegkit.Session session = FFmpegKit.execute(format);

            if (session.getReturnCode().isValueSuccess()) {
                try (InputStream in = new FileInputStream(tempOutFile);
                     OutputStream out = context.getContentResolver().openOutputStream(outFileDoc.getUri())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }

                tempOutFile.delete();
                if (inputFile.exists()) inputFile.delete();

                File parentDir = inputFile.getParentFile();
                if (parentDir != null && parentDir.exists()) {
                    deleteRecursive(parentDir);
                }

                callback.onComplete(outFileDoc.getUri().toString());
            } else {
                callback.onError("❌ TikTok conversion failed: " + session.getFailStackTrace());
            }

        } catch (Exception e) {
            callback.onError("❌ Error: " + e.getMessage());
        }
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
