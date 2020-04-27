package com.zhipu.middle.common.callback;

import android.bluetooth.BluetoothDevice;

public interface OnConnectListener {

    void onConnect(BluetoothDevice device);

    void onDisconnect(BluetoothDevice device, String error);

    /**
     * 接收到服务端返回数据的回调，在方法在子线程中调用
     *
     * @param data 接收到数据的回调
     */
    void onReceiveData(byte[] data);
}
