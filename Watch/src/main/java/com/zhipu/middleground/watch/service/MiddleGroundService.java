package com.zhipu.middleground.watch.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;

import com.zhipu.middle.common.SampleGattAttributes;
import com.zhipu.middle.common.callback.OnConnectListener;
import com.zhipu.middle.common.connect.ConnectHelper;

import java.util.UUID;

public class MiddleGroundService extends Service implements OnConnectListener {
    private static final String TAG = MiddleGroundService.class.getSimpleName();

    private final static UUID UUID_SERVER = UUID.fromString(SampleGattAttributes.UUID_SERVER);
    private final static UUID UUID_READ_WEATHER = UUID.fromString(SampleGattAttributes.CHAR_READ_WEATHER);
    private final static UUID UUID_WRITE_SMS = UUID.fromString(SampleGattAttributes.CHAR_WRITE_SMS);
    private final static UUID UUID_DESCRIPTOR = UUID.fromString(SampleGattAttributes.UUID_NOTIFY);

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothDevice mBluetoothDevice;
    private ConnectHelper mConnectHelper = new ConnectHelper();

    @Override
    public void onCreate() {
        super.onCreate();
        mConnectHelper.setOnConnectListener(this);
        this.initGATTServer();
        this.initServices();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, TAG + ", onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, TAG + ", onStartCommand");
        mConnectHelper.start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, TAG + ", onDestroy");
        mConnectHelper.stop();
    }

    private void initGATTServer() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //BLE is not supported
            return;
        }

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager == null) {
            //Bluetooth not supported
            return;
        }

        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .build();
        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .build();
        AdvertiseData scanResponseData = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(UUID_SERVER))
                .setIncludeTxPowerLevel(true)
                .build();

        AdvertiseCallback callback = new AdvertiseCallback() {

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "BLE advertisement added successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Failed to add BLE advertisement, reason: " + errorCode);
            }
        };

        BluetoothLeAdvertiser bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponseData, callback);
    }

    private void initServices() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(this, mBluetoothGattServerCallback);
        BluetoothGattService service = new BluetoothGattService(UUID_SERVER, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        //add a read characteristic.
        BluetoothGattCharacteristic gattCharacteristic = new BluetoothGattCharacteristic(UUID_READ_WEATHER,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        //add a descriptor
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID_DESCRIPTOR, BluetoothGattCharacteristic.PERMISSION_WRITE);
        gattCharacteristic.addDescriptor(descriptor);
        service.addCharacteristic(gattCharacteristic);

        /*gattCharacteristic = new BluetoothGattCharacteristic(UUID_READ_SMS,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        //add a descriptor
        descriptor = new BluetoothGattDescriptor(UUID_DESCRIPTOR, BluetoothGattCharacteristic.PERMISSION_WRITE);
        gattCharacteristic.addDescriptor(descriptor);
        service.addCharacteristic(gattCharacteristic);*/

        //add a write characteristic.
        BluetoothGattCharacteristic characteristicWrite = new BluetoothGattCharacteristic(UUID_WRITE_SMS,
                BluetoothGattCharacteristic.PROPERTY_WRITE |
                        BluetoothGattCharacteristic.PROPERTY_READ |
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        descriptor = new BluetoothGattDescriptor(UUID_DESCRIPTOR, BluetoothGattCharacteristic.PERMISSION_READ);
        characteristicWrite.addDescriptor(descriptor);
        service.addCharacteristic(characteristicWrite);

        mBluetoothGattServer.addService(service);
    }

    private BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {

        /**
         * 1.连接状态发生变化时
         */
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(TAG, String.format("onConnectionStateChange：device name = %s, address = %s, status = %s," +
                    " newState = %s", device.getName(), device.getAddress(), status, newState));
            mBluetoothDevice = device;
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.d(TAG, String.format("onServiceAdded：status = %s", status));
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(TAG, String.format("onCharacteristicReadRequest：device name = %s, address = %s,requestId = %s" +
                    ", offset = %s ", device.getName(), device.getAddress(), requestId, offset));
            byte[] value = characteristic.getValue();
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            if (value == null) {
                Log.d(TAG, "data is null");
            } else {
                Log.d(TAG, "data len: " + value.length + ", data: " + new String(value));
            }

            if (characteristic.getUuid() == UUID_READ_WEATHER) {
                sendMessage(characteristic, "从设备返回的天气信息");
            }
        }

        /**
         * 3. onCharacteristicWriteRequest,接收具体的字节
         */
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG, String.format("3.onCharacteristicWriteRequest：device name = %s, address = %s", device.getName(), device.getAddress()));
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            //mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, "CCCCC".getBytes());
            onResponseToClient(value, device, requestId, characteristic);
        }

        /**
         * 2.描述被写入时，在这里执行 bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS...  收，触发 onCharacteristicWriteRequest
         */
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG, String.format("2.onDescriptorWriteRequest：device name = %s, address = %s", device.getName(), device.getAddress()));
            // now tell the connected device that this was all successful
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        /**
         * 5.特征被读取。当回复响应成功后，客户端会读取然后触发本方法
         */
        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.d(TAG, String.format("onDescriptorReadRequest：device name = %s, address = %s, requestId = %s", device.getName(), device.getAddress(), requestId));
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.d(TAG, String.format("5.onNotificationSent：device name = %s, address = %s, status = %s",
                    device.getName(), device.getAddress(), status));
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.e(TAG, String.format("onMtuChanged：mtu = %s", mtu));
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.d(TAG, String.format("onExecuteWrite：requestId = %s", requestId));
        }
    };

    /**
     * 4.处理响应内容
     */
    private void onResponseToClient(byte[] value, BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic) {
        String data = new String(value);
        Log.d(TAG, String.format("4.onResponseToClient：device name = %s, address = %s, requestId = %s," +
                " data = %s", device.getName(), device.getAddress(), requestId, data));
        mBluetoothDevice = device;

        sendMessage(characteristic, "CCCCC");
    }

    private void sendMessage(BluetoothGattCharacteristic characteristic, String message) {
        characteristic.setValue(message.getBytes());
        if (mBluetoothDevice != null) {
            mBluetoothGattServer.notifyCharacteristicChanged(mBluetoothDevice, characteristic, false);
        }
        Log.d(TAG, "4.发送: " + message);
    }

    @Override
    public void onConnect(BluetoothDevice device) {
        Log.d(TAG, TAG + ", onConnect: " + device);
    }

    @Override
    public void onDisconnect(BluetoothDevice device, String error) {
        Log.d(TAG, TAG + ", onDisconnect: " + device + ", error: " + error);
    }

    @Override
    public void onReceiveData(byte[] data) {
        Log.d(TAG, TAG + ", data: " + new String(data));
    }
}
