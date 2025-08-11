package com.hassan.everyvideodownloader.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;

public class FileUtils {
    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();

        // Direct file:// URI
        if ("file".equalsIgnoreCase(scheme)) {
            return uri.getPath();
        }

        // SAF content:// URI
        if ("content".equalsIgnoreCase(scheme)) {

            // If this is a tree URI (user picked a folder)
            if (isTreeUri(uri)) {
                String docId = DocumentsContract.getTreeDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                String relativePath = split.length > 1 ? split[1] : "";

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + relativePath;
                } else {
                    File[] externalDirs = context.getExternalMediaDirs();
                    if (externalDirs != null && externalDirs.length > 1) {
                        for (File dir : externalDirs) {
                            if (dir != null && dir.getAbsolutePath().contains(type)) {
                                return dir.getAbsolutePath().split("/Android")[0] + "/" + relativePath;
                            }
                        }
                    }
                }
            }

            // If it's a single file DocumentProvider URI
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);

                if (isExternalStorageDocument(uri)) {
                    String[] split = docId.split(":");
                    String type = split[0];
                    String relativePath = split.length > 1 ? split[1] : "";
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + relativePath;
                    }
                }
                else if (isDownloadsDocument(uri)) {
                    if (docId.startsWith("raw:")) {
                        return docId.substring(4);
                    }
                    try {
                        Uri contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), Long.parseLong(docId)
                        );
                        return getDataColumn(context, contentUri, null, null);
                    } catch (NumberFormatException e) {
                        // Handle special cases where docId isn't a number
                    }
                }
                else if (isMediaDocument(uri)) {
                    String[] split = docId.split(":");
                    String type = split[0];
                    Uri contentUri = null;
                    if ("image".equals(type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    }
                    String selection = "_id=?";
                    String[] selectionArgs = new String[]{split[1]};
                    return getDataColumn(context, contentUri, selection, selectionArgs);
                }
            }

            // Last fallback: try direct content resolver query
            return getDataColumn(context, uri, null, null);
        }

        return null;
    }

    private static boolean isTreeUri(Uri uri) {
        return uri != null && uri.toString().contains("/tree/");
    }

    // Helper method
    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } catch (Exception e) {
            // Some Uris (like tree Uris) will fail — ignore
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

}
