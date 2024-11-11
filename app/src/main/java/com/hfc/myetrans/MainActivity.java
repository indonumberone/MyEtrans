package com.hfc.myetrans;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ALL_PERMISSIONS = 1;
    private static final String TAG = "MainActivity";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ListView devicesListView;
    private ArrayList<BluetoothDevice> devicesList;
    private ArrayList<String> deviceNamesList;
    private ArrayAdapter<String> devicesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicesListView = findViewById(R.id.devices_list_view);
        devicesList = new ArrayList<>();
        deviceNamesList = new ArrayList<>();
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNamesList);
        devicesListView.setAdapter(devicesAdapter);

        // Initialize Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Check if Bluetooth is supported on the device
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Request necessary permissions
        checkPermissions();

        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                BluetoothDevice selectedDevice = devicesList.get(position);
                // Stop scanning before connecting
                stopScan();
                // Start MainMenuActivity and pass the selected device
                Intent intent = new Intent(MainActivity.this, MainMenuActivity.class);
                intent.putExtra("device", selectedDevice);
                startActivity(intent);
            }
        });
    }

    private void checkPermissions() {
        ArrayList<String> permissionList = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionList.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), REQUEST_ALL_PERMISSIONS);
        } else {
            startBluetooth();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            boolean allGranted = true;

            for (int result : grantResults) {
                allGranted = allGranted && (result == PackageManager.PERMISSION_GRANTED);
            }

            if (allGranted) {
                startBluetooth();
            } else {
                Toast.makeText(this, "Permissions are required to scan for BLE devices", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startBluetooth() {
        // Ensures Bluetooth is enabled on the device
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ALL_PERMISSIONS);
        } else {
            startScan();
        }
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        devicesList.clear();
        deviceNamesList.clear();
        devicesAdapter.notifyDataSetChanged();

        bluetoothLeScanner.startScan(leScanCallback);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (!devicesList.contains(device)) {
                devicesList.add(device);
                String deviceName = device.getName();

                if (deviceName == null || deviceName.isEmpty()) {
                    deviceName = device.getAddress();
                }

                deviceNamesList.add(deviceName);
                devicesAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            String errorMessage;
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    errorMessage = "Already scanning...";
                    break;
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMessage = "Scan failed: app registration failed";
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorMessage = "Scan failed: feature unsupported";
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                default:
                    errorMessage = "Scan failed with error code " + errorCode;
            }
            Log.e(TAG, errorMessage);
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }

    };

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
    }
}
