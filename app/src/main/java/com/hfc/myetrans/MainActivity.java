package com.hfc.myetrans;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public static final int MESSAGE_READ = 2;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID TEMPERATURE_CHAR_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd");
    private static final UUID SPEED_CHAR_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abe");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ArrayAdapter<String> mBTArrayAdapter;
    private Handler handler;
    private final ArrayList<BluetoothDevice> devicesList = new ArrayList<>();
    private TextView tvReceived;
    private BluetoothGatt bluetoothGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        setupBluetooth();
        registerBluetoothReceiver();
    }

    private void initUI() {
        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView devicesListView = findViewById(R.id.devices_list_view);
        devicesListView.setAdapter(mBTArrayAdapter);
        devicesListView.setOnItemClickListener(deviceClickListener);

        tvReceived = findViewById(R.id.tv_received);
        handler = new Handler();
    }

    private void setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showToast("Device does not support Bluetooth");
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            requestEnableBluetooth();
        } else {
            checkAndRequestPermissions();
        }
    }

    private void requestEnableBluetooth() {
        ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        checkAndRequestPermissions();
                    } else {
                        showAlertDialog("Bluetooth Required", "Bluetooth must be enabled to proceed.");
                    }
                }
        );

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Enable", (dialog, which) -> requestEnableBluetooth())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            startBLEScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBLEScan();
            } else {
                showToast("Permission denied. Cannot start BLE scan.");
            }
        }
    }

    private void startBLEScan() {
        if (!bluetoothAdapter.isEnabled()) {
            showToast("Please enable Bluetooth");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showToast("Location permission is required for scanning");
            return;
        }

        try {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                handler.postDelayed(() -> {
                    try {
                        bluetoothLeScanner.stopScan(leScanCallback);
                        showToast("Scan stopped");
                    } catch (SecurityException e) {
                        Log.e(TAG, "Security exception stopping scan", e);
                    }
                }, 10000);

                bluetoothLeScanner.startScan(leScanCallback);
                showToast("Scanning for BLE devices...");
            } else {
                showToast("Bluetooth LE Scanner not available");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during BLE scan", e);
            showToast("Failed to start BLE scan due to security issues");
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                if (device != null && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    String deviceName = device.getName();
                    if (deviceName != null && !devicesList.contains(device)) {
                        devicesList.add(device);
                        mBTArrayAdapter.add(deviceName + "\n" + device.getAddress());
                        mBTArrayAdapter.notifyDataSetChanged();
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception when accessing device name", e);
                showToast("Unable to access device name due to permissions.");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            showToast("Scan failed: Error code " + errorCode);
        }
    };


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            runOnUiThread(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    showToast("Connected to GATT server.");
                    try {
                        // Check for location permission before discovering services
                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            bluetoothGatt.discoverServices();
                        } else {
                            showToast("Location permission required for discovering services.");
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Security exception during service discovery", e);
                        showToast("Failed to discover services due to security issues.");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    showToast("Disconnected from GATT server.");
                    closeGatt();
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleServicesDiscovered(gatt);
            } else {
                Log.w(TAG, "onServicesDiscovered received with error status: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristicChanged(characteristic);
        }
    };

    private void handleServicesDiscovered(BluetoothGatt gatt) {
        BluetoothGattService desiredService = gatt.getService(SERVICE_UUID);
        if (desiredService != null) {
            setupCharacteristicNotifications(gatt, desiredService);
        } else {
            Log.w(TAG, "Desired service not found: " + SERVICE_UUID);
        }
    }

    private void setupCharacteristicNotifications(BluetoothGatt gatt, BluetoothGattService service) {
        enableNotificationForCharacteristic(gatt, service.getCharacteristic(TEMPERATURE_CHAR_UUID), "temperature");
        enableNotificationForCharacteristic(gatt, service.getCharacteristic(SPEED_CHAR_UUID), "speed");
    }

    private void enableNotificationForCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, String name) {
        if (characteristic != null) {
            try {
                // Check permission before enabling notifications
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    gatt.setCharacteristicNotification(characteristic, true);

                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                    Log.i(TAG, "Enabled notification for " + name + " characteristic: " + characteristic.getUuid());
                } else {
                    showToast("Location permission required for enabling notifications.");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception when enabling notification for " + name, e);
                showToast("Failed to enable notification for " + name + " due to security issues.");
            }
        } else {
            Log.w(TAG, name + " characteristic not found.");
        }
    }

    private void handleCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        String receivedData = characteristic.getStringValue(0);
        runOnUiThread(() -> {
            if (characteristic.getUuid().equals(TEMPERATURE_CHAR_UUID)) {
                tvReceived.setText("Temperature: " + receivedData + "°C");
            } else if (characteristic.getUuid().equals(SPEED_CHAR_UUID)) {
                tvReceived.append("\nSpeed: " + receivedData + " km/h");
            }
        });
        Log.i(TAG, "Characteristic changed: " + characteristic.getUuid() + " - Value: " + receivedData);
    }

    private final AdapterView.OnItemClickListener deviceClickListener = (adapterView, view, position, id) -> {
        BluetoothDevice selectedDevice = devicesList.get(position);
        try {
            // Pastikan izin diberikan sebelum mengakses nama perangkat
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                String deviceName = selectedDevice.getName();
                if (deviceName != null) {
                    showToast("Selected: " + deviceName);
                } else {
                    showToast("Device name unavailable");
                }

                // Memastikan izin juga diberikan sebelum melakukan koneksi GATT
                bluetoothGatt = selectedDevice.connectGatt(this, false, gattCallback);
            } else {
                showToast("Location permission is required to connect to the device.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when accessing device or connecting to GATT", e);
            showToast("Failed to connect to device due to security issues.");
        }
    };

    private void closeGatt() {
        if (bluetoothGatt != null) {
            try {
                // Memastikan penutupan GATT aman dengan penanganan SecurityException
                bluetoothGatt.close();
                bluetoothGatt = null;
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception when closing GATT", e);
                showToast("Failed to close GATT due to security issues.");
            }
        }
    }


    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        closeGatt();
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    showToast("Bluetooth turned off");
                }
            }
        }
    };
}
