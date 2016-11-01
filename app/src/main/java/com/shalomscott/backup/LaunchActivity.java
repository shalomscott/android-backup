package com.shalomscott.backup;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.Toolbar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.shalomscott.backup.Utils.DriveUtils;


public class LaunchActivity extends Activity implements DriveUtils.OnConnectListener {

    private static final int REQUEST_READ_EXTERNAL_STORAGE = 0;
    private static final int REQUEST_DRIVE_ACCOUNT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Setup UI
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        setActionBar((Toolbar) findViewById(R.id.toolbar));
        getActionBar().setDisplayShowTitleEnabled(false);

        FrameLayout rootLayout = (FrameLayout) findViewById(R.id.root_layout);
        rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                // TODO: figure out disconnecting google api
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Setup write permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            DriveUtils.connect(this, this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    DriveUtils.connect(this, this);
                } else {
                    // TODO: make better error UI
                    Toast.makeText(this, "This app needs read permission to continue",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    @Override
    public void onConnect(DriveUtils.ConnectResult result) {
        if (result.success) {
            // TODO: make better UI
            Toast.makeText(this, "Drive account connected", Toast.LENGTH_SHORT).show();
        } else {
            ConnectionResult failedResult = result.failedResult;
            if (failedResult.hasResolution()) {
                try {
                    failedResult.startResolutionForResult(this, REQUEST_DRIVE_ACCOUNT);
                } catch (IntentSender.SendIntentException e) {
                    // Unable to resolve, message user appropriately
                    // TODO: make better error UI
                }
            } else {
                // TODO: make better error UI
                GoogleApiAvailability.getInstance()
                        .getErrorDialog(this, failedResult.getErrorCode(), 0).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_setting, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, final MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_about) {
            new AlertDialog.Builder(this)
                    .setView(R.layout.dialog_about)
                    .show();
            return true;
        }
        return false;
    }

    public void runBackup(View view) {
        Intent intent = new Intent(this, BackupService.class);
        startService(intent);
    }

    public void sync(View view) {
        DriveUtils.sync();
    }
}