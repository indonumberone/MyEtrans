package com.hfc.myetrans;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

public class MainMenuActivity extends AppCompatActivity {
    private static final String TAG = "MainMenuActivity";

    // UUIDs from ESP32 code
    private static final String SERVICE_UUID = "12345678-1234-1234-1234-123456789abc";
    private static final String TEMPERATURE_CHAR_UUID = "12345678-1234-1234-1234-123456789abd";
    private static final String SPEED_CHAR_UUID = "12345678-1234-1234-1234-123456789abe";

    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic temperatureCharacteristic;
    private BluetoothGattCharacteristic speedCharacteristic;

    private TextView tvTemperature;
    private TextView tvSpeed;
    private TextView tvConnectionStatus;
    SpeedometerView speed;

    // **Declare isConnected here**
    private boolean isConnected = false;

    private static final int REQUEST_CONNECT_PERMISSIONS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        tvTemperature = findViewById(R.id.tv_temperature);
        tvSpeed = findViewById(R.id.tv_speed);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);


        // Get the BluetoothDevice from the intent
        device = getIntent().getParcelableExtra("device");

        if (device == null) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Request necessary permissions
        checkPermissions();

        // Speedometer gils
        // Initialize SpeedometerView
        speed = findViewById(R.id.speedometer);
        speed.setLabelConverter(new SpeedometerView.LabelConverter() {
            @Override
            public String getLabelFor(double progress, double maxProgress) {
                return String.valueOf((int) Math.round(progress));
            }
        });

        // Configure value range and ticks
        speed.setMaxSpeed(100);
        speed.setMajorTickStep(25);
        speed.setMinorTicks(0);

        // Configure value range colors
        speed.addColoredRange(0, 50, Color.GREEN);
        speed.addColoredRange(50, 75, Color.CYAN);
        speed.addColoredRange(75, 100, Color.RED);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Permissions for Android 12 and above
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CONNECT_PERMISSIONS);
            } else {
                // Permission already granted
                connectToDevice();
            }
        } else {
            // Permissions are not required for versions below Android 12
            connectToDevice();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CONNECT_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice();
            } else {
                Toast.makeText(this, "Permission required to connect to device", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        if (device == null) return;
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    // Implement BluetoothGattCallback methods
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    isConnected = true;
                    runOnUiThread(() -> {
                        tvConnectionStatus.setText("Connected");
                        tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        runOnUiThread(() -> Toast.makeText(MainMenuActivity.this, "Terhubung ke GATT server", Toast.LENGTH_SHORT).show());
                    });
                    // Discover services
                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    isConnected = false;
                    runOnUiThread(() -> {
                        tvConnectionStatus.setText("Disconnected");
                        tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        runOnUiThread(() -> Toast.makeText(MainMenuActivity.this, "Koneksi ke GATT server terputus", Toast.LENGTH_SHORT).show());
                    });
                    gatt.close();
                }
            } else {
                Log.w(TAG, "onConnectionStateChange received: " + status);
                gatt.close();
                isConnected = false;
                runOnUiThread(() -> {
                    tvConnectionStatus.setText("Disconnected");
                    tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                });
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                if (service != null) {
                    temperatureCharacteristic = service.getCharacteristic(UUID.fromString(TEMPERATURE_CHAR_UUID));
                    speedCharacteristic = service.getCharacteristic(UUID.fromString(SPEED_CHAR_UUID));

                    // Read initial values
                    readCharacteristic(temperatureCharacteristic);
                    readCharacteristic(speedCharacteristic);

                    // Set up notifications
                    setCharacteristicNotification(temperatureCharacteristic, true);
                    setCharacteristicNotification(speedCharacteristic, true);
                } else {
                    Log.e(TAG, "Service not found!");
                    runOnUiThread(() -> Toast.makeText(MainMenuActivity.this, "Service not found", Toast.LENGTH_SHORT).show());
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicRead(final BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristic(characteristic);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicChanged(final BluetoothGatt gatt,
                                            final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            handleCharacteristic(characteristic);
        }
    };

    private void handleCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final String receivedData = characteristic.getStringValue(0);
        runOnUiThread(() -> {
            if (characteristic.getUuid().equals(UUID.fromString(TEMPERATURE_CHAR_UUID))) {
                tvTemperature.setText(receivedData);
            } else if (characteristic.getUuid().equals(UUID.fromString(SPEED_CHAR_UUID))) {
                tvSpeed.setText(receivedData);

                // gils spedometer
                try {
                    TextView tvSpeedSegment = findViewById(R.id.tv_speed_segment);

                    String numericPart = receivedData.replace("kmh", "").trim();
                    double parsedSpeedValue = Double.parseDouble(numericPart);

                    speed.setSpeed(parsedSpeedValue, 1000, 100);
                    tvSpeedSegment.setText(String.valueOf((int) parsedSpeedValue));

                    if (parsedSpeedValue < 20) {
                        tvSpeedSegment.setTextColor(Color.BLUE);
                    } else if (parsedSpeedValue < 40) {
                        tvSpeedSegment.setTextColor(Color.CYAN);
                    } else if (parsedSpeedValue < 60) {
                        tvSpeedSegment.setTextColor(Color.GREEN);
                    } else if (parsedSpeedValue < 80) {
                        tvSpeedSegment.setTextColor(Color.YELLOW);
                    } else if (parsedSpeedValue < 100) {
                        tvSpeedSegment.setTextColor(Color.MAGENTA);
                    } else {
                        tvSpeedSegment.setTextColor(Color.RED);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse speed: " + receivedData, e);
                }
            }

        });
    }

    @SuppressLint("MissingPermission")
    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) return;
        bluetoothGatt.readCharacteristic(characteristic);
    }

    @SuppressLint("MissingPermission")
    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (bluetoothGatt == null || characteristic == null) return;

        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        // For notifications to work, we need to write to the client characteristic configuration descriptor (CCCD)
        UUID descriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
        if (descriptor != null) {
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
