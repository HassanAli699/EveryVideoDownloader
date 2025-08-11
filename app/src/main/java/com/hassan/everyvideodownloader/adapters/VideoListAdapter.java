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
    private final List<File> videos;

    public VideoListAdapter(Context context, List<File> videos) {
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
        File video = videos.get(position);
        holder.title.setText(video.getName());
        File file = new File(video.getAbsolutePath());
        // Create thumbnail
        Glide.with(context)
                .load(file)
                .thumbnail(.5f)
                .into(holder.thumbnail);


        holder.itemView.setOnClickListener(v -> {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    "com.hassan.everyvideodownloader.fileprovider",
                    video
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
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
