package com.zhipu.middleground.communication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.Executors;

public class BluetoothHelper {
    public static final String TAG = BluetoothHelper.class.getSimpleName();
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;

    private final BroadcastReceiver mFindBlueToothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    Log.d(TAG, "discovery start");
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Log.d(TAG, "discovery finish");
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    if (bluetoothDevice == null) {
                        Log.d(TAG, "found device, but BluetoothDevice is null");
                    } else {
                        String address = bluetoothDevice.getAddress();
                        String name = bluetoothDevice.getName();
                        Log.d(TAG, "found device, address: " + address + ", name: " + name
                                + ", bond state: " + bluetoothDevice.getBondState());
                        /*mBluetoothList.add(bluetoothDevice);
                        mBluetoothListAdapter.notifyDataSetChanged();*/

                        if ("BE:FC:46:00:00:02".equals(address)) {//Android Bluedroid
                            bondTargetDevice(bluetoothDevice);
                        }
                    }
                    break;
                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    Log.d(TAG, "device pairing request");
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    if (bluetoothDevice == null) {
                        Log.d(TAG, "bond state changed, but BluetoothDevice is null");
                    } else {
                        int bondState = bluetoothDevice.getBondState();
                        String msg = "bond state changed, address: " + bluetoothDevice.getAddress()
                                + ", name: " + bluetoothDevice.getName() + ", bond state: " + bondState
                                + ", thread: " + Thread.currentThread().getName();
                        switch (bluetoothDevice.getBondState()) {
                            case BluetoothDevice.BOND_BONDING:
                                msg += ", bond_bonding......";
                                break;
                            case BluetoothDevice.BOND_BONDED:
                                msg += ", bonded finish";
                                //BluetoothConnectActivity.openPage(BluetoothListActivity.this, bluetoothDevice.getAddress());
                                break;
                            case BluetoothDevice.BOND_NONE:
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

    public BluetoothHelper(Context context) {
        mContext = context;
    }

    public void init() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter actionDiscoveryStarted = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);//搜索开始
        IntentFilter actionDiscoveryFinished = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//搜索结束
        IntentFilter actionFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);//寻找到设备
        IntentFilter actionPairingRequest = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);//配对请求
        IntentFilter actionBondStateChanged = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//配对状态改变
        mContext.registerReceiver(mFindBlueToothReceiver, actionDiscoveryStarted);
        mContext.registerReceiver(mFindBlueToothReceiver, actionDiscoveryFinished);
        mContext.registerReceiver(mFindBlueToothReceiver, actionFound);
        mContext.registerReceiver(mFindBlueToothReceiver, actionBondStateChanged);
        mContext.registerReceiver(mFindBlueToothReceiver, actionPairingRequest);
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
    private void getBondedDevice() {
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        if (bondedDevices == null || bondedDevices.size() == 0) {
            Log.d(TAG, "no paired devices");
        } else {
            for (BluetoothDevice bondedDevice : bondedDevices) {
                Log.d(TAG, "paired device address: " + bondedDevice.getAddress() + ", name: "
                        + bondedDevice.getName() + ", bond state: " + bondedDevice.getBondState());
                //mBluetoothList.add(bondedDevice);
            }
        }

        this.doDiscovery();
    }

    private void doDiscovery() {
        Executors.newCachedThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                if (mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }
                mBluetoothAdapter.startDiscovery();
            }
        });
    }

    /**
     * 配对蓝牙设备
     */
    private void bondTargetDevice(BluetoothDevice bluetoothDevice) {
        mBluetoothAdapter.cancelDiscovery(); //配对之前，停止搜索
        if (bluetoothDevice == null) {
            return;
        }
        if (bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED) {//没配对才配对
            Log.d(TAG, "start create bond");
            if (bluetoothDevice.createBond()) {
                Log.d(TAG, "create bond successful");
            } else {
                Log.d(TAG, "create bond fail");
            }
        }
    }

    public void release() {
    }
}
