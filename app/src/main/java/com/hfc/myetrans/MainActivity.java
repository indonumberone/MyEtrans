package com.hfc.myetrans;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final String TAG = MainActivity.class.getSimpleName();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;
    private Handler handler;
    private ArrayAdapter<String> mBTArrayAdapter;
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds.
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;
    private ListView mDevicesListView;
    private TextView msgBluetooth;
    private BluetoothSocket mBTSocket = null;
    private ConnectedThread mConnectedThread;
    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mDevicesListView = findViewById(R.id.devices_list_view);
        msgBluetooth = findViewById(R.id.msg_bluetooth);
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);
        mDevicesListView.setAdapter(mBTArrayAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler(msg -> {
            switch (msg.what) {
                case CONNECTING_STATUS:
                    if (msg.arg1 == 1) {
                        String deviceName = (String) msg.obj;
                        msgBluetooth.setText("Connected to " + deviceName);
                    } else {
                        msgBluetooth.setText("Failed to connect");
                    }
                    break;

                case MESSAGE_READ:
                    // Konversi byte[] ke String dan tampilkan di msgBluetooth
                    byte[] readBuf = (byte[]) msg.obj;
                    String sensorData = new String(readBuf, 0, msg.arg1);
                    msgBluetooth.setText("Sensor Data: " + sensorData);
                    break;
            }
            return true;
        });


        // Check if device supports Bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Register for Bluetooth enable request
        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        checkPermissionsAndStartScan();
                    } else {
                        Toast.makeText(this, "Bluetooth must be enabled to proceed", Toast.LENGTH_SHORT).show();
                        showEnableBluetoothDialog();
                    }
                }
        );

        // Check if Bluetooth is enabled; request to enable it if not
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        } else {
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
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
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
                .setNegativeButton("Cancel", (dialog, which) -> showEnableBluetoothDialog())
                .show();
    }

    @SuppressLint("MissingPermission")
    private void startBLEScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        handler.postDelayed(() -> {
            bluetoothLeScanner.stopScan(leScanCallback);
            Toast.makeText(MainActivity.this, "Scan stopped", Toast.LENGTH_SHORT).show();
        }, SCAN_PERIOD);

        bluetoothLeScanner.startScan(leScanCallback);
        Toast.makeText(this, "Scanning for BLE devices...", Toast.LENGTH_SHORT).show();
        discover();
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            @SuppressLint("MissingPermission") String deviceName = device.getName();
            String deviceAddress = device.getAddress();
         //   Toast.makeText(MainActivity.this, "Found device: " + deviceName + " (" + deviceAddress + ")", Toast.LENGTH_SHORT).show();
            Log.d("info", "Found device: " + deviceName + " (" + deviceAddress + ")");
        }

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(MainActivity.this, "Scan failed with error code: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBLEScan();
            } else {
                Toast.makeText(this, "Location permission is required for BLE scanning", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private final AdapterView.OnItemClickListener mDeviceClickListener = (adapterView, view, position, id) -> {
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        String info = ((TextView) view).getText().toString();
        final String address = info.substring(info.length() - 17);
        final String name = info.substring(0,info.length() - 17);
        new Thread()
        {
            @Override
            public void run() {
                boolean fail = false;

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

                try {
                    mBTSocket = createBluetoothSocket(device);
                } catch (IOException e) {
                    fail = true;
                    Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
                }
                // Establish the Bluetooth socket connection.
                try {
                    mBTSocket.connect();
                } catch (IOException e) {
                    try {
                        fail = true;
                        mBTSocket.close();
                        handler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget();
                    } catch (IOException e2) {
                        //insert code to deal with this
                        Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
                    }
                }
                if(!fail) {
                    mConnectedThread = new ConnectedThread(mBTSocket, handler);
                    mConnectedThread.start();

                    handler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget();
                }
            }
        }.start();
    };

    @SuppressLint("MissingPermission")
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            Method method = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) method.invoke(device, BT_MODULE_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e);
            return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
        }
    }
    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void discover(){
        // Check if the device is already discovering
        if(bluetoothAdapter.isDiscovering()){
            bluetoothAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),getString(R.string.DisStop),Toast.LENGTH_SHORT).show();
        }
        else{
            if(bluetoothAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                bluetoothAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), getString(R.string.DisStart), Toast.LENGTH_SHORT).show();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                mDevicesListView.setOnItemClickListener((adapterView, view, position, id) -> {
                    if (!bluetoothAdapter.isEnabled()) {
                        Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String info = ((TextView) view).getText().toString();
                    final String address = info.substring(info.length() - 17);
                    final String name = info.substring(0, info.length() - 17);
                    Log.i("testing",name);

                    BluetoothDevice selectedDevice = bluetoothAdapter.getRemoteDevice(address);

                    connectToDevice(selectedDevice);
                });

            }
            else{
                Toast.makeText(getApplicationContext(), getString(R.string.BTnotOn), Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        unregisterReceiver(blReceiver);
        if (mBTSocket != null) {
            try {
                mBTSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close Bluetooth socket", e);
            }

        }

    }
    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            boolean fail = false;

            try {
                mBTSocket = createBluetoothSocket(device);
                mBTSocket.connect();
            } catch (IOException e) {
                fail = true;
                runOnUiThread(() -> Toast.makeText(getBaseContext(), "Failed to connect to device", Toast.LENGTH_SHORT).show());
                try {
                    mBTSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close socket", closeException);
                }
            }

            if (!fail) {
                // Jika koneksi berhasil, inisialisasi thread untuk komunikasi dan kirim pesan status ke handler
                mConnectedThread = new ConnectedThread(mBTSocket, handler);
                mConnectedThread.start();

                // Kirim pesan ke Handler dengan nama perangkat untuk menampilkan di msgBluetooth
                handler.obtainMessage(CONNECTING_STATUS, 1, -1, device.getName()).sendToTarget();
            } else {
                // Jika koneksi gagal, kirim pesan ke Handler untuk menampilkan "Failed to connect"
                handler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
            }
        }).start();
    }


}
