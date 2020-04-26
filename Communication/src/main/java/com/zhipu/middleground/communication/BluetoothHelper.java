package com.zhipu.middleground.communication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.zhipu.middleground.communication.callback.OnBondListener;
import com.zhipu.middleground.communication.callback.OnDiscoveryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class BluetoothHelper {
    public static final String TAG = BluetoothHelper.class.getSimpleName();

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BleHelper mBleHelper;

    private OnDiscoveryListener mOnDiscoveryListener;
    private OnBondListener mOnBondListener;

    public BluetoothHelper(Context context) {
        mContext = context;
        mBleHelper = new BleHelper();
    }

    public void initialize() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBleHelper.initialize(mContext);
    }

    public boolean isSupportBluetooth() {
        return (mBluetoothAdapter != null);
    }

    public boolean isBluetoothEnabled() {
        return (this.isSupportBluetooth() && mBluetoothAdapter.isEnabled());
    }

    /**
     * 获取已经配对的设备
     */
    public List<BluetoothDevice> getBondedDevice() {
        List<BluetoothDevice> bluetoothDevices = new ArrayList<>();
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        if (bondedDevices == null || bondedDevices.size() == 0) {
            Log.d(TAG, "no bonded devices");
        } else {
            for (BluetoothDevice bondedDevice : bondedDevices) {
                Log.d(TAG, "bonded device address: " + bondedDevice.getAddress() + ", name: "
                        + bondedDevice.getName() + ", bond state: " + bondedDevice.getBondState());
                bluetoothDevices.add(bondedDevice);
            }
        }

        return bluetoothDevices;
    }

    /**
     * 开始搜索设备
     */
    public void startDiscovery() {
        this.registerReceivers();

        Executors.newCachedThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                cancelDiscovery();
                mBluetoothAdapter.startDiscovery();
            }
        });
    }

    /**
     * 停止搜索设备
     */
    public void cancelDiscovery() {
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * 配对蓝牙设备
     */
    public boolean isNoneBond(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null) {
            return false;
        } else {
            return bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED;
        }
    }

    /**
     * 配对蓝牙设备
     */
    public void bondTargetDevice(BluetoothDevice bluetoothDevice) {
        this.cancelDiscovery();//配对之前，停止搜索

        if (this.isNoneBond(bluetoothDevice)) {//没配对才配对
            Log.d(TAG, "start create bond");
            if (bluetoothDevice.createBond()) {
                Log.d(TAG, "create bond successful");
            } else {
                Log.w(TAG, "create bond fail");
            }
        }
    }

    public void release() {
        this.cancelDiscovery();
        mContext.unregisterReceiver(mBlueToothReceiver);

        mBleHelper.release();
    }

    private void registerReceivers() {
        IntentFilter actionDiscoveryStarted = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);//搜索开始
        IntentFilter actionDiscoveryFinished = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//搜索结束
        IntentFilter actionFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);//寻找到设备
        IntentFilter actionPairingRequest = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);//配对请求
        IntentFilter actionBondStateChanged = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//配对状态改变
        mContext.registerReceiver(mBlueToothReceiver, actionDiscoveryStarted);
        mContext.registerReceiver(mBlueToothReceiver, actionDiscoveryFinished);
        mContext.registerReceiver(mBlueToothReceiver, actionFound);
        mContext.registerReceiver(mBlueToothReceiver, actionBondStateChanged);
        mContext.registerReceiver(mBlueToothReceiver, actionPairingRequest);
    }

    public void setOnDiscoveryListener(OnDiscoveryListener onDiscoveryListener) {
        mOnDiscoveryListener = onDiscoveryListener;
    }

    public void setOnBondListener(OnBondListener onBondListener) {
        mOnBondListener = onBondListener;
    }

    public BleHelper getBleHelper() {
        return mBleHelper;
    }

    private final BroadcastReceiver mBlueToothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    Log.d(TAG, "discovery start");
                    if (mOnDiscoveryListener != null) {
                        mOnDiscoveryListener.onStart();
                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Log.d(TAG, "discovery finish");
                    if (mOnDiscoveryListener != null) {
                        mOnDiscoveryListener.onFinish();
                    }
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    if (device == null) {
                        Log.d(TAG, "found device, but BluetoothDevice is null");
                    } else {
                        Log.d(TAG, "found device, address: " + device.getAddress() + ", name: "
                                + device.getName() + ", bond state: " + device.getBondState());
                        if (mOnDiscoveryListener != null) {
                            mOnDiscoveryListener.onFound(device);
                        }
                    }
                    break;
                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    Log.d(TAG, "device pairing request");
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    if (device == null) {
                        Log.d(TAG, "bond state changed, but BluetoothDevice is null");
                    } else {
                        final int bondState = device.getBondState();
                        String msg = "bond state changed, address: " + device.getAddress()
                                + ", name: " + device.getName() + ", bond state: " + bondState;
                        switch (bondState) {
                            case BluetoothDevice.BOND_BONDING:
                                msg += ", bond_bonding......";
                                if (mOnBondListener != null) {
                                    mOnBondListener.onBonding();
                                }
                                break;
                            case BluetoothDevice.BOND_BONDED:
                                msg += ", bonded finish";
                                if (mOnBondListener != null) {
                                    mOnBondListener.onBonded(device);
                                }
                                break;
                            case BluetoothDevice.BOND_NONE:
                                if (mOnBondListener != null) {
                                    mOnBondListener.onCancel();
                                }
                                msg += ", bond cancel";
                            default:
                                break;
                        }
                        Log.d(TAG, msg);
                    }
                    break;
                default:
                    break;
            }
        }
    };

}
