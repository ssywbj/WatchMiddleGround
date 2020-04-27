package com.zhipu.middleground.app.activity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhipu.middleground.app.R;
import com.zhipu.middleground.app.adapter.RecyclerAdapter;
import com.zhipu.middleground.app.view.RecyclerItemDecoration;
import com.zhipu.middleground.communication.BluetoothHelper;
import com.zhipu.middleground.communication.ConnectHelper;
import com.zhipu.middleground.communication.callback.OnBondListener;
import com.zhipu.middleground.communication.callback.OnConnectListener;
import com.zhipu.middleground.communication.callback.OnDiscoveryListener;

import java.util.ArrayList;
import java.util.List;

public class BluetoothListActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private BluetoothListAdapter mBluetoothListAdapter;
    private BluetoothHelper mBluetoothHelper = new BluetoothHelper(this);
    private List<BluetoothDevice> mBluetoothDevices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_list_aty);
        this.initRecyclerView();

        mBluetoothHelper.initialize();

        if (mBluetoothHelper.isSupportBluetooth()) {
            if (mBluetoothHelper.isBluetoothEnabled()) {
                this.findDevices();
            } else {
                Log.d(BluetoothHelper.TAG, "bluetooth isn't enabled, request action enable!");
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);//请求用户允许使用蓝牙
                startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
            }
        } else {
            Log.w(BluetoothHelper.TAG, "this device isn't support bluetooth!");
            finish();
        }
    }

    private void findDevices() {
        mBluetoothDevices.addAll(mBluetoothHelper.getBondedDevice());
        mBluetoothHelper.startDiscovery();

        mBluetoothHelper.setOnDiscoveryListener(new OnDiscoveryListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onFound(BluetoothDevice bluetoothDevice) {
                mBluetoothDevices.add(bluetoothDevice);
                mBluetoothListAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFinish() {
            }
        });
    }

    private void initRecyclerView() {
        mBluetoothListAdapter = new BluetoothListAdapter(mBluetoothDevices);
        mBluetoothListAdapter.setOnItemClickListener(new RecyclerAdapter.OnItemClickListener<BluetoothDevice>() {
            @Override
            public void onItemClick(View view, BluetoothDevice data, int position) {
                if (mBluetoothHelper.isNoneBond(data)) {
                    mBluetoothHelper.bondTargetDevice(data);
                    mBluetoothHelper.setOnBondListener(new OnBondListener() {
                        @Override
                        public void onBonding() {
                        }

                        @Override
                        public void onBonded(BluetoothDevice bluetoothDevice) {
                            DeviceControlActivity.openPage(BluetoothListActivity.this,
                                    bluetoothDevice.getName(), bluetoothDevice.getAddress());
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    //DeviceControlActivity.openPage(BluetoothListActivity.this, data.getName(), data.getAddress());

                    mBluetoothHelper.cancelDiscovery();

                    ConnectHelper connectHelper = mBluetoothHelper.getConnectHelper();
                    if (connectHelper.isConnected()) {
                        connectHelper.write("dfadfadfa".getBytes());
                    } else {
                        connectHelper.connect(data.getAddress(), false);
                    }
                    connectHelper.setOnConnectListener(new OnConnectListener() {
                        @Override
                        public void onConnect(BluetoothDevice device) {
                            Toast.makeText(BluetoothListActivity.this, "连接成功: " + device.getName(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onDisconnect(BluetoothDevice device, String error) {
                            Toast.makeText(BluetoothListActivity.this, "连接异常: " + device.getName() + ", " + error, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onReceiveData(final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(BluetoothListActivity.this, "data: " +
                                            new String(data), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });

                    /*BleConnectHelper bleHelper = mBluetoothHelper.getBleHelper();
                    if (!bleHelper.isSupportBle()) {
                        return;
                    }
                    if (bleHelper.isConnected()) {
                        //bleHelper.disconnect();
                        bleHelper.write("FFFFFFFF");
                    } else {
                        bleHelper.connect(data.getAddress());
                    }
                    bleHelper.setConnectBleListener(new OnConnectBleListener() {
                        @Override
                        public void onConnect(BluetoothDevice device) {
                            Toast.makeText(BluetoothListActivity.this, "连接成功: " + device.getName(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onDisconnect(BluetoothDevice device) {
                            Toast.makeText(BluetoothListActivity.this, "断开连接: " + device.getName(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onReceiveData(final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(BluetoothListActivity.this, "data: " +
                                            new String(data), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });*/
                }
            }
        });

        RecyclerView recyclerView = findViewById(R.id.bluetooth_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new RecyclerItemDecoration(this, true));
        recyclerView.setAdapter(mBluetoothListAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBluetoothHelper.getConnectHelper().start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothHelper.release();
        mBluetoothDevices.clear();
        mBluetoothHelper.getConnectHelper().stop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {//用户允许使用蓝牙
                this.findDevices();
            } else {
                Log.d(BluetoothHelper.TAG, "bluetooth isn't enabled after request action enable!");
            }
        }
    }

    private static final class BluetoothListAdapter extends RecyclerAdapter<BluetoothDevice> {

        BluetoothListAdapter(List<BluetoothDevice> dataList) {
            super(dataList);
        }

        @Override
        protected void bindView(RecyclerView.ViewHolder viewHolder, int position, BluetoothDevice data) {
            if (viewHolder instanceof ContentHolder) {
                ContentHolder contentHolder = (ContentHolder) viewHolder;
                if (TextUtils.isEmpty(data.getName())) {
                    contentHolder.textName.setText(data.getAddress());
                } else {
                    contentHolder.textName.setText(data.getName());
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ContentHolder(getItemLayout(parent.getContext(), R.layout.bluetooth_list_aty_adt));
        }

        static class ContentHolder extends RecyclerView.ViewHolder {
            TextView textName;

            ContentHolder(View view) {
                super(view);
                textName = view.findViewById(R.id.text_bluetooth_name);
            }
        }
    }

}
