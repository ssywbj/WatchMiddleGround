package com.zhipu.middleground.communication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zhipu.middle.common.SampleGattAttributes;
import com.zhipu.middleground.communication.callback.OnBleConnectListener;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

public class BleHelper {
    private final static String TAG = BluetoothHelper.TAG;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private static final int MSG_ON_CONNECT = 1;
    private static final int MSG_ON_DISCONNECT = 2;

    private Context mContext;
    private OnBleConnectListener mOnBleConnectListener;
    private UiHandler mUiHandler;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    public void initialize(Context context) {
        mContext = context;
        mUiHandler = new UiHandler(this);

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return;
        }

        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public boolean isSupportBle() {
        return (mBluetoothAdapter != null);
    }

    public void connect(String address) {
        if (!this.isSupportBle()) {
            Log.w(TAG, "this device isn't support ble");
            return;
        }

        if (address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found, unable to connect!");
            return;
        }

        device.connectGatt(mContext, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
    }

    public boolean isConnected() {
        return (mConnectionState == STATE_CONNECTED);
    }

    public void disconnect() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();

        mBluetoothGatt = null;
    }

    public void release() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        mUiHandler.removeMessages(MSG_ON_CONNECT);
        mUiHandler.removeMessages(MSG_ON_DISCONNECT);
    }

    private void write(byte[] cmd, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return;
        }
        characteristic.setValue(cmd);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        mBluetoothGatt.setCharacteristicNotification(characteristic, true);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public void write(byte[] data) {
        if (mBluetoothGatt == null) {
            return;
        }
        BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(SampleGattAttributes.UUID_SERVER));
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(SampleGattAttributes.CHAR_WRITE_SMS));
        this.write(data, characteristic);
    }

    public void write(String msg) {
        this.write(msg.getBytes());
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    public void setOnBleConnectListener(OnBleConnectListener onBleConnectListener) {
        mOnBleConnectListener = onBleConnectListener;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /**
         * 设备连接状态改变时的回调
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            BluetoothDevice device = gatt.getDevice();
            Log.d(TAG, "onConnectionStateChange, status: " + status + ", newState: " + newState
                    + ", device name: " + device.getName() + ", address: " + device.getAddress());
            Message msg = new Message();
            msg.obj = device;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Attempting to start service discovery: " + gatt.discoverServices());
                mConnectionState = STATE_CONNECTED;
                msg.what = MSG_ON_CONNECT;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                mConnectionState = STATE_DISCONNECTED;
                msg.what = MSG_ON_DISCONNECT;
            }
            mUiHandler.sendMessage(msg);
        }

        /**
         * 连接成功后UUID服务信息的回调，注意：需要在onConnectionStateChange方法连接成功的状态中调用
         * BluetoothGatt.discoverServices()方法，才会执行该方法
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            List<BluetoothGattService> services = gatt.getServices();
            Log.d(TAG, "onServicesDiscovered, status: " + status + ", getServices: " + services.size());
            if (status != BluetoothGatt.GATT_SUCCESS) {
                return;
            }
            mBluetoothGatt = gatt;
        }

        /**
         * 设备返回数据时的回调
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] data = characteristic.getValue();
            Log.d(TAG, "onCharacteristicChanged data: " + data.length);
            if (SampleGattAttributes.CHAR_WRITE_SMS.equals(characteristic.getUuid().toString())) {
                onReceiveDataResponse(data);
            }
        }

        /**
         * 成功读取设备数据时的回调
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicRead status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {//接收到蓝牙发送的数据
            }
        }

        /**
         * 成功向设备写入数据时的回调
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite status: " + status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorRead status: " + status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite status: " + status);
        }
    };

    private void onConnectResponse(BluetoothDevice bluetoothDevice) {
        if (mOnBleConnectListener != null) {
            mOnBleConnectListener.onConnect(bluetoothDevice);
        }
    }

    private void onDisconnectResponse(BluetoothDevice bluetoothDevice) {
        if (mOnBleConnectListener != null) {
            mOnBleConnectListener.onDisconnect(bluetoothDevice);
        }
    }

    private void onReceiveDataResponse(byte[] data) {
        if (mOnBleConnectListener != null) {
            mOnBleConnectListener.onReceiveData(data);
        }
    }

    private static class UiHandler extends Handler {
        private WeakReference<BleHelper> mWeakReference;

        UiHandler(BleHelper bleHelper) {
            mWeakReference = new WeakReference<>(bleHelper);
        }

        @Override
        public void dispatchMessage(@NonNull Message msg) {
            super.dispatchMessage(msg);
            BleHelper bleHelper = mWeakReference.get();
            if (bleHelper == null) {
                return;
            }

            switch (msg.what) {
                case MSG_ON_CONNECT:
                    if (msg.obj instanceof BluetoothDevice) {
                        bleHelper.onConnectResponse((BluetoothDevice) msg.obj);
                    }
                    break;
                case MSG_ON_DISCONNECT:
                    if (msg.obj instanceof BluetoothDevice) {
                        bleHelper.onDisconnectResponse((BluetoothDevice) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    }

}
