package com.hassan.everyvideodownloader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hassan.everyvideodownloader.helpers.YtDlpHelper;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;
    private EditText urlInput;
    private Button downloadBtn;
    private YtDlpHelper ytDlpHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlInput = findViewById(R.id.urlInput);
        downloadBtn = findViewById(R.id.downloadBtn);

        ytDlpHelper = new YtDlpHelper(this);

        downloadBtn.setOnClickListener(v -> {
            if (checkPermissions()) {
                startDownload();
            } else {
                requestPermissions();
            }
        });
    }

    private void startDownload() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Enter a valid URL", Toast.LENGTH_SHORT).show();
            return;
        }

        ytDlpHelper.downloadVideo(url, new YtDlpHelper.DownloadCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(String error) {
                Log.d("ERROR WHILE DOWNLOADING VIDEO", "onError: " + error);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this,
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
                Toast.makeText(this, "Permission denied for storage", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
