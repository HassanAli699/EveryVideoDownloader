package com.hassan.everyvideodownloader.fragments;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hassan.everyvideodownloader.R;
import com.hassan.everyvideodownloader.adapters.VideoListAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadedFragment extends Fragment {

    private RecyclerView videosRecycler;

    public DownloadedFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_downloaded, container, false);

        videosRecycler = view.findViewById(R.id.videosRecycler);

        loadDownloadedVideos();

        return view;
    }

    private void loadDownloadedVideos() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "Every Downloader");

        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles();
        List<File> videoFiles = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".mp4")) {
                    videoFiles.add(f);
                }
            }

            // Sort by latest modified first
            videoFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        }

        VideoListAdapter adapter = new VideoListAdapter(getContext(), videoFiles);
        videosRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        videosRecycler.setAdapter(adapter);
    }


    @Override
    public void onResume() {
        super.onResume();
        loadDownloadedVideos(); // Refresh list when fragment becomes visible again
    }
}
