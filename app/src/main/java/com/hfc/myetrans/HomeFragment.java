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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.UUID;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    // UUIDs for the Bluetooth service and characteristics
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
    private SpeedometerView speed;

    private boolean isConnected = false;
    private static final int REQUEST_CONNECT_PERMISSIONS = 2;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize UI components
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvSpeed = view.findViewById(R.id.tv_speed);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        speed = view.findViewById(R.id.speedometer);

        // Set up SpeedometerView
        setupSpeedometer();

        // Retrieve BluetoothDevice from MainMenuActivity
        if (getActivity() != null) {
            device = getActivity().getIntent().getParcelableExtra("device");
        }

        if (device == null) {
            Toast.makeText(getContext(), "No device selected", Toast.LENGTH_SHORT).show();
        } else {
            checkPermissions();
        }

        return view;
    }

    private void setupSpeedometer() {
        speed.setLabelConverter((progress, maxProgress) -> String.valueOf((int) Math.round(progress)));
        speed.setMaxSpeed(100);
        speed.setMajorTickStep(25);
        speed.setMinorTicks(0);
        speed.addColoredRange(0, 50, Color.GREEN);
        speed.addColoredRange(50, 75, Color.CYAN);
        speed.addColoredRange(75, 100, Color.RED);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CONNECT_PERMISSIONS);
            } else {
                connectToDevice();
            }
        } else {
            connectToDevice();
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        if (device == null) return;
        bluetoothGatt = device.connectGatt(getContext(), false, gattCallback);
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
                    getActivity().runOnUiThread(() -> {
                        tvConnectionStatus.setText("Connected");
                        tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        Toast.makeText(getContext(), "Terhubung ke GATT server", Toast.LENGTH_SHORT).show();
                    });
                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    isConnected = false;
                    getActivity().runOnUiThread(() -> {
                        tvConnectionStatus.setText("Disconnected");
                        tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        Toast.makeText(getContext(), "Koneksi ke GATT server terputus", Toast.LENGTH_SHORT).show();
                    });
                    gatt.close();
                }
            } else {
                Log.w(TAG, "onConnectionStateChange received: " + status);
                gatt.close();
                isConnected = false;
                getActivity().runOnUiThread(() -> {
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

                    readCharacteristic(temperatureCharacteristic);
                    readCharacteristic(speedCharacteristic);

                    setCharacteristicNotification(temperatureCharacteristic, true);
                    setCharacteristicNotification(speedCharacteristic, true);
                } else {
                    Log.e(TAG, "Service not found!");
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Service not found", Toast.LENGTH_SHORT).show());
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristic(characteristic);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            handleCharacteristic(characteristic);
        }
    };

    private void handleCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final String receivedData = characteristic.getStringValue(0);
        getActivity().runOnUiThread(() -> {
            if (characteristic.getUuid().equals(UUID.fromString(TEMPERATURE_CHAR_UUID))) {
                tvTemperature.setText(receivedData);
            } else if (characteristic.getUuid().equals(UUID.fromString(SPEED_CHAR_UUID))) {
                tvSpeed.setText(receivedData);

                try {
                    String numericPart = receivedData.replace("kmh", "").trim();
                    double parsedSpeedValue = Double.parseDouble(numericPart);

                    speed.setSpeed(parsedSpeedValue, 1000, 100);
                    TextView tvSpeedSegment = getActivity().findViewById(R.id.tv_speed_segment);
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
        UUID descriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
        if (descriptor != null) {
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
