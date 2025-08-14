package com.hassan.everyvideodownloader.fragments;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import com.hassan.everyvideodownloader.R;
import com.hassan.everyvideodownloader.services.DownloadService;
import com.hassan.everyvideodownloader.utils.Utils;
import java.net.HttpURLConnection;
import java.net.URL;


public class HomeFragment extends Fragment {

    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_CODE_PICK_FOLDER = 2001;
    private static final String PREFS_NAME = "every_video_downloader_prefs";
    private static final String KEY_FOLDER_URI = "selected_folder_uri";

    private EditText urlInput;
    private Button downloadBtn;
    private ProgressBar downloadProgress;
    private TextView progressText;
    private ImageView changeFolderBtn, pasteLinkBtn;

    private String downloadUrl;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DownloadService.ACTION_PROGRESS.equals(action)) {
                int percent = intent.getIntExtra("percent", -1);
                String status = intent.getStringExtra("status");

                downloadProgress.setVisibility(VISIBLE);
                progressText.setVisibility(VISIBLE);

                // ✅ If waiting or processing → indeterminate spinner
                if ("Waiting For Response...".equalsIgnoreCase(status) || "Processing...".equalsIgnoreCase(status)  || "Making Video Upload Ready Wait...".equalsIgnoreCase(status)) {
                    downloadProgress.setIndeterminate(true);
                    progressText.setText(status); // just show text
                }
                // ✅ Otherwise → show determinate progress with % text
                else if (percent >= 0) {
                    downloadProgress.setIndeterminate(false);
                    downloadProgress.setProgress(percent);
                    progressText.setText(percent + "% Downloaded");
                }
                // ✅ Fallback for unknown state
                else {
                    downloadProgress.setIndeterminate(true);
                    progressText.setText(status != null ? status : "Working...");
                }

            } else if (DownloadService.ACTION_COMPLETE.equals(action)) {
                progressText.setText("Download Complete ✅ \nYour video is now ready to be shared!");
                downloadProgress.setVisibility(GONE);
            } else if (DownloadService.ACTION_ERROR.equals(action)) {
                String error = intent.getStringExtra("error");
                downloadProgress.setVisibility(View.GONE);
                progressText.setVisibility(View.VISIBLE);
                progressText.setText(error != null ? error : "Unknown error ❌ \nTry again make sure the URl is working");

                Utils.showAnimatedToast(
                        getActivity(),
                        error != null ? error : "Something went wrong",
                        R.drawable.alert_error,
                        Utils.ToastDuration.SHORT
                );
            }
        }
    };

    public HomeFragment() {
        // Required empty constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        urlInput = view.findViewById(R.id.urlInput);
        downloadBtn = view.findViewById(R.id.downloadBtn);
        changeFolderBtn = view.findViewById(R.id.modifyFoldersBtn);
        downloadProgress = view.findViewById(R.id.downloadProgress);
        progressText = view.findViewById(R.id.progressText);
        pasteLinkBtn = view.findViewById(R.id.pasteLink);

        setupPasteButton();
        setupDownloadButton();
        setupChangeFolderButton();

        return view;
    }

    private String resolveFinalUrl(String shortUrl) {
        try {
            String currentUrl = shortUrl;
            int maxRedirects = 5; // prevent infinite loops

            for (int i = 0; i < maxRedirects; i++) {
                HttpURLConnection connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                        code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == 307 || code == 308) {

                    String location = connection.getHeaderField("Location");
                    if (location != null && !location.isEmpty()) {
                        // Handle relative redirects
                        if (!location.startsWith("http")) {
                            URL base = new URL(currentUrl);
                            location = new URL(base, location).toString();
                        }
                        currentUrl = location;
                    } else {
                        break;
                    }
                } else {
                    // No more redirect
                    break;
                }
            }
            return currentUrl;
        } catch (Exception e) {
           Log.e("ERROR", "ERROR WHILE GETTING URL" + e.getMessage());
            return shortUrl;
        }
    }

    private void setupPasteButton() {
        pasteLinkBtn.setOnClickListener(v -> {
            if ("clear".equals(pasteLinkBtn.getTag())) {
                urlInput.setText("");
                pasteLinkBtn.setImageResource(R.drawable.copy);
                pasteLinkBtn.setTag("paste");
            } else {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    ClipData clipData = clipboard.getPrimaryClip();
                    if (clipData != null && clipData.getItemCount() > 0) {
                        CharSequence pastedText = clipData.getItemAt(0).coerceToText(getContext());
                        if (pastedText != null) {
                            urlInput.setText(pastedText.toString());
                            pasteLinkBtn.setImageResource(R.drawable.clear);
                            pasteLinkBtn.setTag("clear");
                            Utils.showAnimatedToast(
                                    getActivity(),
                                    "Link pasted",
                                    R.drawable.check_mark,
                                    Utils.ToastDuration.SHORT
                            );
                            // Toast.makeText(getContext(), "📋 ", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Utils.showAnimatedToast(
                            getActivity(),
                            "Clipboard is empty",
                            R.drawable.alert_error,
                            Utils.ToastDuration.SHORT
                    );
                   // Toast.makeText(getContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupDownloadButton() {
        downloadBtn.setOnClickListener(v -> {
            if (checkPermissions()) {
                downloadProgress.setVisibility(VISIBLE);
                downloadProgress.setIndeterminate(true);
                progressText.setVisibility(VISIBLE);
                progressText.setText("Starting Download...");
                startDownload();
            } else {
                requestPermissions();
            }
        });
    }

    private void setupChangeFolderButton() {
        changeFolderBtn.setOnClickListener(v -> pickFolder());
    }

    private void startDownload() {
        String inputUrl = urlInput.getText().toString().trim();
        if (inputUrl.isEmpty()) {
            Utils.showAnimatedToast(
                    getActivity(),
                    "Please Enter A Valid URL",
                    R.drawable.warning,
                    Utils.ToastDuration.SHORT
            );
            return;
        }

        // 🔹 If it's a Pinterest short link, expand it first
        if (inputUrl.contains("pin.it/")) {
            new Thread(() -> {
                String expandedUrl = resolveFinalUrl(inputUrl);
                Log.d("HomeFragment", "Expanded URL OF PINTREST: " + expandedUrl);
                requireActivity().runOnUiThread(() -> {
                    downloadUrl = expandedUrl;
                    proceedWithDownload();
                });
            }).start();
        } else {
            downloadUrl = inputUrl;
            proceedWithDownload();
        }
    }

    private void proceedWithDownload() {
        Uri savedFolder = getSavedFolderUri();
        if (isFolderValid(savedFolder)) {
            if (downloadUrl != null) {
                startDownloadService(savedFolder);
            }
          //  startDownloadService(savedFolder);
        } else {
            Utils.showAnimatedToast(
                    getActivity(),
                    "Please choose a valid download folder",
                    R.drawable.warning,
                    Utils.ToastDuration.SHORT
            );
            pickFolder();
        }
    }

    private void startDownloadService(Uri folderUri) {
        Log.d("HomeFragment", "Starting DownloadService for: " + downloadUrl + " to folder: " + folderUri);

        Intent serviceIntent = new Intent(getContext(), DownloadService.class);
        serviceIntent.putExtra("url", downloadUrl);
        serviceIntent.putExtra("folderUri", folderUri.toString());

        ContextCompat.startForegroundService(requireContext(), serviceIntent);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_PROGRESS);
        filter.addAction(DownloadService.ACTION_COMPLETE);
        filter.addAction(DownloadService.ACTION_ERROR);
        ContextCompat.registerReceiver(requireContext(), downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

//    @Override
//    public void onPause() {
//        super.onPause();
//        requireContext().unregisterReceiver(downloadReceiver);
//    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == getActivity().RESULT_OK && data != null) {
            Uri pickedFolderUri = data.getData();
            requireContext().getContentResolver().takePersistableUriPermission(
                    pickedFolderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
            saveFolderUri(pickedFolderUri);
            if (downloadUrl != null) {
                startDownloadService(pickedFolderUri);
            }
        }
    }

    private boolean isFolderValid(Uri folderUri) {
        if (folderUri == null) return false;
        boolean hasPermission = false;
        for (UriPermission perm : requireContext().getContentResolver().getPersistedUriPermissions()) {
            if (perm.getUri().equals(folderUri) && perm.isWritePermission()) {
                hasPermission = true;
                break;
            }
        }
        if (!hasPermission) return false;
        DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), folderUri);
        return folder != null && folder.exists() && folder.isDirectory();
    }

    private void saveFolderUri(Uri uri) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply();
    }

    private Uri getSavedFolderUri() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriStr = prefs.getString(KEY_FOLDER_URI, null);
        return uriStr != null ? Uri.parse(uriStr) : null;
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        } else {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pickFolder();
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDownload();
        } else {
            Utils.showAnimatedToast(
                    getActivity(),
                    "Permission denied for storage",
                    R.drawable.alert_error,
                    Utils.ToastDuration.SHORT
            );
        }
    }
}

