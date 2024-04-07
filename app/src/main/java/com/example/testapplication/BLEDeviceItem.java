package com.example.testapplication;

import android.bluetooth.BluetoothDevice;

public class BLEDeviceItem {
    public BluetoothDevice device;

    public enum ConnectionState{
        UNCONNECTED, CONNECTED, BROKEN
    };
    public ConnectionState state;

    public boolean selected;

    public BLEDeviceItem(BluetoothDevice device){
        this.device = device;

        state = ConnectionState.UNCONNECTED;
        selected = false;
    }
}
