package com.hfc.myetrans;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.hfc.myetrans.databinding.FragmentPowerBinding;

import java.util.UUID;


public class PowerFragment extends Fragment {
    private static final String SERVICE_UUID = "12345678-1234-1234-1234-123456789abc";
    private static final String TEMPERATURE_CHAR_UUID = "12345678-1234-1234-1234-123456789abd";
    private static final String BATTERY_LEVEL_CHAR_UUID = "12345678-1234-1234-1234-123456789bba";
    private static final String BATTERY_VOLTAGE_CHAR_UUID = "12345678-1234-1234-1234-123456789bbb";
    private static final String BATTERY_STATUS_CHAR_UUID = "12345678-1234-1234-1234-123456789bbc";

    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic temperatureCharacteristic;
    private BluetoothGattCharacteristic batteryLevelCharacteristic;
    private BluetoothGattCharacteristic batteryVoltageCharacteristic;
    private BluetoothGattCharacteristic batteryStatusCharacteristic;

    private static final int REQUEST_CONNECT_PERMISSIONS = 2;
    private boolean isConnected = false;

    private FragmentPowerBinding binding;
    private BroadcastReceiver batteryInfoReceiver;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Initialize binding and inflate layout
        binding = FragmentPowerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        registerReceiver();

        // Retrieve BluetoothDevice from MainMenuActivity
        if (getActivity() != null) {
            device = getActivity().getIntent().getParcelableExtra("device");
        }

        if (device == null) {
            Toast.makeText(getContext(), "No device selected", Toast.LENGTH_SHORT).show();
        } else {
            checkPermissions();
        }
    }

//    private void registerReceiver() {
//        batteryInfoReceiver = new BroadcastReceiver() {
//            @SuppressLint("SetTextI18n")
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                int batteryLevel = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) : 0;
//                int batteryIsCharging = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) : 0;
//                int batteryTemperature = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10 : 0;
//                int batteryVoltage = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000 : 0;
//                String batteryTechnology = intent != null ? intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) : "Unknown";
//
//                // Update view with battery information
//                binding.batteryProgress.setProgress(100 - batteryLevel);
//                binding.tvBatteryLevel.setText(batteryLevel + "%");
//                binding.tvPlugInValue.setText(batteryIsCharging == 0 ? "plug out" : "plug in");
//                binding.tvVoltageValue.setText(batteryVoltage + " V");
//                binding.tvTemperatureValue.setText(batteryTemperature + " C");
//                binding.tvTechnologyValue.setText(batteryTechnology);
//            }
//        };
//
//        if (getContext() != null) {
//            getContext().registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//        }
//    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
                        Toast.makeText(getContext(), "Berhasil mendapatkan data energ", Toast.LENGTH_SHORT).show();
                    });
                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    isConnected = false;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Koneksi ke GATT server terputus", Toast.LENGTH_SHORT).show();
                    });
                    gatt.close();
                }
            } else {
                Log.w(TAG, "onConnectionStateChange received: " + status);
                gatt.close();
                isConnected = false;
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
                    batteryLevelCharacteristic = service.getCharacteristic(UUID.fromString(BATTERY_LEVEL_CHAR_UUID));
                    batteryVoltageCharacteristic = service.getCharacteristic(UUID.fromString(BATTERY_VOLTAGE_CHAR_UUID));
                    batteryStatusCharacteristic = service.getCharacteristic(UUID.fromString(BATTERY_STATUS_CHAR_UUID));

                    readCharacteristic(temperatureCharacteristic);
                    readCharacteristic(batteryLevelCharacteristic);
                    readCharacteristic(batteryVoltageCharacteristic);
                    readCharacteristic(batteryStatusCharacteristic);

                    setCharacteristicNotification(temperatureCharacteristic, true);
                    setCharacteristicNotification(batteryLevelCharacteristic, true);
                    setCharacteristicNotification(batteryVoltageCharacteristic, true);
                    setCharacteristicNotification(batteryStatusCharacteristic, true);
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
                binding.tvTemperatureValue.setText(receivedData);
            } else if (characteristic.getUuid().equals(UUID.fromString(BATTERY_LEVEL_CHAR_UUID))) {
                binding.tvBatteryLevel.setText(receivedData + "%");
                int batteryLevel = Integer.parseInt(receivedData);
                binding.batteryProgress.setProgress(100 - batteryLevel);
            } else if (characteristic.getUuid().equals(UUID.fromString(BATTERY_VOLTAGE_CHAR_UUID))) {
                binding.tvVoltageValue.setText(receivedData);
            } else if (characteristic.getUuid().equals(UUID.fromString(BATTERY_STATUS_CHAR_UUID))) {
            binding.tvPlugInValue.setText(receivedData);
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
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null && batteryInfoReceiver != null) {
            getContext().unregisterReceiver(batteryInfoReceiver);
        }
        binding = null;
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}