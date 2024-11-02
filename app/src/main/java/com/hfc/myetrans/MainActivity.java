package com.hfc.myetrans;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
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
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final String TAG = MainActivity.class.getSimpleName();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    private BluetoothSocket mBTSocket = null;
    private Handler handler;
    private ArrayList<BluetoothDevice> devicesList = new ArrayList<>();
    private BluetoothDevice selectedDevice;
    private TextView tvReceived;
    private BluetoothGatt bluetoothGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler();
        tvReceived = findViewById(R.id.tv_received);

        if (bluetoothAdapter == null) {
            showToast("Device does not support Bluetooth");
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            requestEnableBluetooth();
        } else {
            checkPermissionsAndStartScan();
        }

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void initUI() {
        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mDevicesListView = findViewById(R.id.devices_list_view);
        mDevicesListView.setAdapter(mBTArrayAdapter);
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);
    }

    private void requestEnableBluetooth() {
        ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        checkPermissionsAndStartScan();
                    } else {
                        showAlertDialog("Bluetooth Required", "Bluetooth must be enabled to proceed.");
                    }
                }
        );

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    private void checkPermissionsAndStartScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        } else {
            startBLEScan();
        }
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Enable", (dialog, which) -> requestEnableBluetooth())
                .setNegativeButton("Cancel", (dialog, which) -> showAlertDialog(title, message))
                .show();
    }

    @SuppressLint("MissingPermission")
    private void startBLEScan() {
        if (!bluetoothAdapter.isEnabled()) {
            showToast("Please enable Bluetooth");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        handler.postDelayed(() -> {
            bluetoothLeScanner.stopScan(leScanCallback);
            showToast("Scan stopped");
        }, 10000); // Stop scanning after 10 seconds

        bluetoothLeScanner.startScan(leScanCallback);
        showToast("Scanning for BLE devices...");
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getName() != null && !devicesList.contains(device)) {
                devicesList.add(device);
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                showToast("Connected to GATT server.");
                // Start service discovery
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                showToast("Disconnected from GATT server.");
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered.");

                // Assuming you know the UUID of the service you are interested in
                UUID serviceUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Replace with your actual service UUID
                BluetoothGattService service = gatt.getService(serviceUUID);

                if (service != null) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.i(TAG, "Characteristic: " + characteristic.getUuid());

                        // You can read the characteristic or set notifications here
                        // Example of reading a characteristic:
                        gatt.readCharacteristic(characteristic);
                    }
                } else {
                    Log.w(TAG, "Service not found: " + serviceUUID);
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String receivedData = characteristic.getStringValue(0);
                runOnUiThread(() -> tvReceived.setText(receivedData));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String receivedData = characteristic.getStringValue(0);
            runOnUiThread(() -> tvReceived.setText(receivedData));
        }
    };

    private final AdapterView.OnItemClickListener mDeviceClickListener = (adapterView, view, position, id) -> {
        selectedDevice = devicesList.get(position);
        showToast("Selected: " + selectedDevice.getName());

        // Connect to the BLE device
        bluetoothGatt = selectedDevice.connectGatt(this, false, gattCallback);
    };

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    showAlertDialog("Bluetooth Required", "Please enable Bluetooth to proceed.");
                }
            }
        }
    };
}
