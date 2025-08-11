package com.hassan.everyvideodownloader.adapters;



import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;


import com.bumptech.glide.Glide;
import com.hassan.everyvideodownloader.R;

import java.io.File;
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

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri videoUri = videos.get(position);

        // Get filename
        String fileName = videoUri.getLastPathSegment();
        if (fileName != null && fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        holder.title.setText(fileName != null ? fileName : "Video");

        // Glide thumbnail from SAF Uri
        Glide.with(context)
                .load(videoUri)
                .thumbnail(0.5f)
                .into(holder.thumbnail);

        // Play video when clicked
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView thumbnail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.videoTitle);
            thumbnail = itemView.findViewById(R.id.videoThumbnail);
        }
    }
}

