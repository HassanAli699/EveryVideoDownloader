package com.hassan.everyvideodownloader.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.hassan.everyvideodownloader.R;

import java.io.IOException;
import java.util.List;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.ViewHolder> {

    private final Context context;
    private final List<Uri> videos;

    public VideoListAdapter(Context context, List<Uri> videos) {
        this.context = context;
        this.videos = videos;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri videoUri = videos.get(position);

        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, videoUri);
            String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                long durationMs = Long.parseLong(durationStr);
                long seconds = (durationMs / 1000) % 60;
                long minutes = (durationMs / (1000 * 60)) % 60;
                holder.videoDuration.setText(String.format("%02d:%02d", minutes, seconds));
            } else {
                holder.videoDuration.setText("--:--");
            }
        } catch (Exception e) {
            e.printStackTrace();
            holder.videoDuration.setText("--:--");
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                 new RuntimeException(e);
            }
        }
        // Glide thumbnail from SAF Uri
        Glide.with(context)
                .load(videoUri)
                .thumbnail(Glide.with(context)
                        .load(videoUri)
                        .sizeMultiplier(0.5f))
                .into(holder.thumbnail);


        // Play video on click
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        });

        // Show dialog on long press
        holder.itemView.setOnLongClickListener(v -> {
            String[] options = {"Show details", "Delete video", "Share"};

            new android.app.AlertDialog.Builder(context)
                    .setTitle("Choose an action")
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0: // Show details
                                try {
                                    showVideoDetails(videoUri);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                break;

                            case 1: // Delete video
                                deleteVideo(videoUri, position);
                                break;

                            case 2: // Share
                                shareVideo(videoUri);
                                break;
                        }
                    })
                    .show();
            return true;
        });
    }
    private void showVideoDetails(Uri videoUri) throws IOException {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        retriever.setDataSource(context, videoUri);

        String duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
        String width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
        String bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE);
        String framerate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);

        // File size and date modified
        long fileSize = 0;
        long lastModified = 0;
        try {
            java.io.File file = new java.io.File(videoUri.getPath());
            if (file.exists()) {
                fileSize = file.length(); // in bytes
                lastModified = file.lastModified();
            } else {
                // SAF approach (when path is null or different)
                try (android.database.Cursor cursor = context.getContentResolver()
                        .query(videoUri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                        int dateIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED);
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex);
                        if (dateIndex != -1) lastModified = cursor.getLong(dateIndex) * 1000L; // seconds → ms
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        retriever.release();

        // Format details
        String fileSizeMB = String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        String dateModified = (lastModified > 0)
                ? new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(new java.util.Date(lastModified))
                : "Unknown";

        String details = "Resolution: " + width + "x" + height +
                "\nDuration: " + (Integer.parseInt(duration) / 1000) + " sec" +
                "\nMIME Type: " + mimeType +
                "\nFile Size: " + fileSizeMB +
                "\nDate Modified: " + dateModified +
                "\nBitrate: " + (bitrate != null ? (Integer.parseInt(bitrate) / 1000) + " kbps" : "Unknown") +
                "\nFramerate: " + (framerate != null ? framerate + " fps" : "Unknown");

        new android.app.AlertDialog.Builder(context)
                .setTitle("Video Details")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show();
    }

    private void deleteVideo(Uri videoUri, int position) {
        DocumentFile file = DocumentFile.fromSingleUri(context, videoUri);
        if (file != null && file.canWrite()) {
            if (file.delete()) {
                videos.remove(position);
                notifyItemRemoved(position);
            }
        } else {
            // Can't delete, maybe ask user to pick again with ACTION_OPEN_DOCUMENT
        }
    }
    private void shareVideo(Uri videoUri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("video/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, videoUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "Share video via"));
    }


    @Override
    public int getItemCount() {
        return videos.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView videoDuration;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.videoThumbnail);
            videoDuration = itemView.findViewById(R.id.videoDuration);
        }
    }
}

