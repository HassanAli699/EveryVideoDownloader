package com.hassan.everyvideodownloader.fragments;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hassan.everyvideodownloader.R;
import com.hassan.everyvideodownloader.adapters.VideoListAdapter;
import com.hassan.everyvideodownloader.helpers.YtDlpHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final int REQUEST_PERMISSION = 100;

    private EditText urlInput;
    private Button downloadBtn;
    private ProgressBar downloadProgress;
    private TextView progressText;
    private YtDlpHelper ytDlpHelper;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize UI elements
        urlInput = view.findViewById(R.id.urlInput);
        downloadBtn = view.findViewById(R.id.downloadBtn);
        downloadProgress = view.findViewById(R.id.downloadProgress);
        progressText = view.findViewById(R.id.progressText);

        ytDlpHelper = new YtDlpHelper(getContext());

        // Load existing videos on launch
        // loadDownloadedVideos();

        downloadBtn.setOnClickListener(v -> {
            if (checkPermissions()) {
                startDownload();
            } else {
                requestPermissions();
            }
        });

        return view;
    }

    private void startDownload() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(getContext(), "Enter a valid URL", Toast.LENGTH_SHORT).show();
            return;
        }

        ytDlpHelper.downloadVideo(url, new YtDlpHelper.DownloadProgressCallback() {
            @Override
            public void onProgress(int percent, String statusText) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    downloadProgress.setVisibility(VISIBLE);
                    progressText.setVisibility(VISIBLE);

                    int currentProgress = downloadProgress.getProgress();
                    int targetProgress = percent;

                    ObjectAnimator animation = ObjectAnimator.ofInt(downloadProgress, "progress",
                            currentProgress, targetProgress);
                    animation.setDuration(300);
                    animation.setInterpolator(new DecelerateInterpolator());
                    animation.start();

                    progressText.setText(statusText);
                });
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onComplete(String folderPath) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressText.setText("✅ Downloaded to: " + folderPath);
                    downloadProgress.setVisibility(GONE);
                    progressText.setVisibility(GONE);
                   // loadDownloadedVideos(); // refresh list after download
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> progressText.setText(error));
            }
        });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownload();
            } else {
                Toast.makeText(getContext(), "Permission denied for storage", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
