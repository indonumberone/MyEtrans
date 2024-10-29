package com.hfc.myetrans;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;
    private static final int REQUEST_ENABLE_BT = 1;
    private Handler handler;
    private Button button;
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds.
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the handler to prevent NullPointerException
        handler = new Handler();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean bluetoothLEAvailable = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);

        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // Start scanning once Bluetooth is enabled
                        checkPermissionsAndStartScan();
                    } else {
                        Toast.makeText(this, "Bluetooth must be enabled to proceed", Toast.LENGTH_SHORT).show();
                        showEnableBluetoothDialog();
                    }
                }
        );

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        } else if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        } else {
            // If Bluetooth is already enabled, proceed to scan
            checkPermissionsAndStartScan();
        }

        // Register receiver for Bluetooth status changes
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void checkPermissionsAndStartScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
        } else {
            startBLEScan();
        }
    }


    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    // Bluetooth dimatikan, tampilkan dialog lagi
                    showEnableBluetoothDialog();
                }
            }
        }
    };

    private void showEnableBluetoothDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Bluetooth Required")
                .setMessage("This application requires Bluetooth to function. Please enable Bluetooth.")
                .setCancelable(false)
                .setPositiveButton("Enable", (dialog, which) -> {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBluetoothLauncher.launch(enableBtIntent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    showEnableBluetoothDialog();
                })
                .show();
    }

    private void startBLEScan() {
        // Ensure Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        // Initialize BluetoothLeScanner
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        // Stop scan after a predefined scan period
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothLeScanner.stopScan(leScanCallback);
                Toast.makeText(MainActivity.this, "Scan stopped", Toast.LENGTH_SHORT).show();
            }
        }, SCAN_PERIOD);

        // Start scanning for BLE devices
        bluetoothLeScanner.startScan(leScanCallback);
        Toast.makeText(this, "Scanning for BLE devices...", Toast.LENGTH_SHORT).show();
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            String deviceAddress = device.getAddress();
            Toast.makeText(MainActivity.this, "Found device: " + deviceName + " (" + deviceAddress + ")", Toast.LENGTH_SHORT).show();
            Log.d("info", "Found device: " + deviceName + " (" + deviceAddress + ")");
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                Toast.makeText(MainActivity.this, "Found device: " + deviceName + " (" + deviceAddress + ")", Toast.LENGTH_SHORT).show();
                Log.d("info", "Found device: " + deviceName + " (" + deviceAddress + ")");
                Log.d("msg", "Found device: " + deviceName + " (" + deviceAddress + ")");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(MainActivity.this, "Scan failed with error code: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBLEScan();
            } else {
                Toast.makeText(this, "Location permission is required for BLE scanning", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
    }


}
