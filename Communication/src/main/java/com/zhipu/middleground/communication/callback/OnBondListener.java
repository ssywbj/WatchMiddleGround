package com.zhipu.middleground.communication.callback;

import android.bluetooth.BluetoothDevice;

public interface OnBondListener {
    void onBonding();

    void onBonded(BluetoothDevice bluetoothDevice);

    void onCancel();
}
