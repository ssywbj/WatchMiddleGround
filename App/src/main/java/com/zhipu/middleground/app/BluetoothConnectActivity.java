package com.zhipu.middleground.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zhipu.middleground.app.connect.BluetoothConnectHelper;

public class BluetoothConnectActivity extends AppCompatActivity {
    //private static final String UUID_NAME = "00001101-0000-1000-8000-00805F9B34FB";
    //private static final String UUID_NAME = "8CE255C0-200A-11E0-AC64-0800200C9A66";
    private static final String UUID_NAME = "00001105-0000-1000-8000-00805f9b34fb";
    private static final String EXTRA_BLUETOOTH_ADDRESS = "data_bluetooth_address";
    private BluetoothConnectHelper mBluetoothConnectHelper = new BluetoothConnectHelper();
    private String mTag;

    public static void openPage(Context context, String address) {
        Intent intent = new Intent(context, BluetoothConnectActivity.class);
        intent.putExtra(EXTRA_BLUETOOTH_ADDRESS, address);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_bluetooth_connect_aty);
        mTag = getClass().getSimpleName();

        String bluetoothAddress = getIntent().getStringExtra(EXTRA_BLUETOOTH_ADDRESS);
        final BluetoothDevice bluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bluetoothAddress);
        Log.d(mTag, "address: " + bluetoothAddress + ", bluetooth device: " + bluetoothDevice);
        mBluetoothConnectHelper.connect(bluetoothDevice, false);

        findViewById(R.id.item_data_int).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage(String.valueOf(v.getId()));
            }
        });

        findViewById(R.id.item_data_string).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage(((TextView) v).getText().toString());
            }
        });

        findViewById(R.id.item_data_object).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage(bluetoothDevice.toString());
            }
        });

        findViewById(R.id.item_data_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sendMessage(bluetoothDevice.toString());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBluetoothConnectHelper.getState() == BluetoothConnectHelper.STATE_NONE) {
            mBluetoothConnectHelper.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothConnectHelper != null) {
            mBluetoothConnectHelper.stop();
        }
    }

    private void sendMessage(String message) {
        if (mBluetoothConnectHelper.getState() != BluetoothConnectHelper.STATE_COMMUNICATE) {
            Toast.makeText(this, "未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            mBluetoothConnectHelper.write(message.getBytes());
        }
    }
}
