package com.zhipu.middleground.communication.callback;

import android.bluetooth.BluetoothDevice;

public interface OnDiscoveryListener {

    void onStart();

    void onFound(BluetoothDevice bluetoothDevice);

    void onFinish();
}
