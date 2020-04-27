package com.zhipu.middleground.communication.callback;

import android.bluetooth.BluetoothDevice;

public interface OnConnectBleListener {

    void onConnectBle(BluetoothDevice device);

    void onDisconnectBle(BluetoothDevice device);

    /**
     * 接收到服务端返回数据的回调，在方法在子线程中调用
     *
     * @param data 接收到数据的回调
     */
    void onReceiveDataBle(byte[] data);
}
