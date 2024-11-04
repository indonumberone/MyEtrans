package com.hfc.myetrans;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hfc.myetrans.databinding.FragmentPowerBinding;


public class PowerFragment extends Fragment {

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
        registerReceiver();
    }

    private void registerReceiver() {
        batteryInfoReceiver = new BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onReceive(Context context, Intent intent) {
                int batteryLevel = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) : 0;
                int batteryIsCharging = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) : 0;
                int batteryTemperature = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10 : 0;
                int batteryVoltage = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000 : 0;
                String batteryTechnology = intent != null ? intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) : "Unknown";

                // Update view with battery information
                binding.batteryProgress.setProgress(100 - batteryLevel);
                binding.tvBatteryLevel.setText(batteryLevel + "%");
                binding.tvPlugInValue.setText(batteryIsCharging == 0 ? "plug out" : "plug in");
                binding.tvVoltageValue.setText(batteryVoltage + " V");
                binding.tvTemperatureValue.setText(batteryTemperature + " C");
                binding.tvTechnologyValue.setText(batteryTechnology);
            }
        };

        if (getContext() != null) {
            getContext().registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null && batteryInfoReceiver != null) {
            getContext().unregisterReceiver(batteryInfoReceiver);
        }
        binding = null;
    }
}