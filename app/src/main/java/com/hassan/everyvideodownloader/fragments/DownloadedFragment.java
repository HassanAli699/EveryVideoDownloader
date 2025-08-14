package com.hassan.everyvideodownloader.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hassan.everyvideodownloader.R;
import com.hassan.everyvideodownloader.adapters.VideoListAdapter;
import com.hassan.everyvideodownloader.utils.Utils;
import java.util.ArrayList;
import java.util.List;

public class DownloadedFragment extends Fragment {

    private RecyclerView videosRecycler;
    private static final String PREFS_NAME = "every_video_downloader_prefs";
    private static final String KEY_FOLDER_URI = "selected_folder_uri";
    private FrameLayout loadingOverlay;

    public DownloadedFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_downloaded, container, false);

        videosRecycler = view.findViewById(R.id.videosRecycler);
        ImageView refreshBtn = view.findViewById(R.id.refreshBtn);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);

        refreshBtn.setOnClickListener(v -> {
            Log.d("DownloadedFragment", "Refresh button clicked");
            loadDownloadedVideos();
            Utils.showAnimatedToast(
                    getActivity(),
                    "Successfully Refreshed",
                    R.drawable.check_mark,
                    Utils.ToastDuration.SHORT
            );
        });



        loadDownloadedVideos();

        return view;
    }

    private void showLoader() {
        if (getActivity() != null) {
            requireActivity().getWindow().setStatusBarColor(
                    android.graphics.Color.parseColor("#303030")
            );
            View decorView = requireActivity().getWindow().getDecorView();
            decorView.setSystemUiVisibility(0);

        }
        loadingOverlay.bringToFront();
        loadingOverlay.setVisibility(View.VISIBLE);
    }


    private void hideLoader() {
        if (getActivity() != null) {
            requireActivity().getWindow().setStatusBarColor(
                    android.graphics.Color.parseColor("#FFFFFF")
            );
            View decorView = requireActivity().getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        loadingOverlay.setVisibility(View.GONE);
    }


    private void loadDownloadedVideos() {
        Uri savedFolderUri = getSavedFolderUri();
        if (savedFolderUri == null) {
            videosRecycler.setAdapter(null);
            return;
        }

        showLoader();
        // loadingOverlay.bringToFront();

        new Thread(() -> {
            List<Uri> videoUris = getVideoUrisFromUri(savedFolderUri);

            // Switch to main thread to update UI
            requireActivity().runOnUiThread(() -> {
                VideoListAdapter adapter = new VideoListAdapter(getContext(), videoUris);

                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
                int numOfColumns = (int) (dpWidth / 180); // 180dp per item
                videosRecycler.setLayoutManager(new GridLayoutManager(getContext(), numOfColumns));
                videosRecycler.setAdapter(adapter);

                hideLoader();

            });
        }).start();
    }

    private Uri getSavedFolderUri() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriStr = prefs.getString(KEY_FOLDER_URI, null);
        return uriStr != null ? Uri.parse(uriStr) : null;
    }


    private List<Uri> getVideoUrisFromUri(Uri folderUri) {
        List<Uri> videoUris = new ArrayList<>();
        DocumentFile pickedFolder = DocumentFile.fromTreeUri(requireContext(), folderUri);
        if (pickedFolder != null && pickedFolder.isDirectory()) {
            List<DocumentFile> mp4Files = new ArrayList<>();

            for (DocumentFile file : pickedFolder.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().endsWith(".mp4")) {
                    mp4Files.add(file);
                }
            }

            // Sort by lastModified in descending order (latest first)
            mp4Files.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            // Convert to URIs
            for (DocumentFile file : mp4Files) {
                videoUris.add(file.getUri());
            }
        }
        return videoUris;
    }


    @Override
    public void onResume() {
        super.onResume();
        loadDownloadedVideos(); // Refresh list when fragment becomes visible again
    }
}
