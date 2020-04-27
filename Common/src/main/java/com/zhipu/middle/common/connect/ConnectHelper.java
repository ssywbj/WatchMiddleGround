package com.zhipu.middle.common.connect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zhipu.middle.common.callback.OnConnectListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;

public class ConnectHelper {
    private static final String TAG = "BluetoothHelper";
    private static final int MSG_ON_CONNECT = 1;
    private static final int MSG_ON_DISCONNECT = 2;

    private static final String NAME_SECURE = "BluetoothConnectSecure";
    private static final String NAME_INSECURE = "BluetoothConnectInsecure";

    private static final UUID UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private static final int STATE_NONE = 0;
    private static final int STATE_LISTEN = 1;
    private static final int STATE_CONNECTING = 2;
    private static final int STATE_CONNECTED = 3;
    private int mState = STATE_NONE;

    private final BluetoothAdapter mBluetoothAdapter;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private CommunicateThread mCommunicateThread;

    private UiHandler mUiHandler;
    private OnConnectListener mOnConnectListener;
    private BluetoothDevice mRemoteDevice;
    private String mDisconnectReason;

    public ConnectHelper() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mUiHandler = new UiHandler(this);
    }

    public synchronized void start() {
        if (mState != STATE_NONE) {
            return;
        }

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mCommunicateThread != null) {
            mCommunicateThread.cancel();
            mCommunicateThread = null;
        }

        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
    }

    public synchronized void connect(String address, boolean secure) {
        mRemoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (mRemoteDevice == null) {
            Log.w(TAG, "RemoteDevice not found or unspecified address. ");
            return;
        }

        Log.d(TAG, "connect to device: " + mRemoteDevice.getName() + ", address: " + mRemoteDevice.getAddress());
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mCommunicateThread != null) {
            mCommunicateThread.cancel();
            mCommunicateThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(mRemoteDevice, secure);
        mConnectThread.start();
    }

    private synchronized void connected(BluetoothSocket socket, String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mCommunicateThread != null) {
            mCommunicateThread.cancel();
            mCommunicateThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mCommunicateThread = new CommunicateThread(socket);
        mCommunicateThread.start();

        mUiHandler.sendEmptyMessage(MSG_ON_CONNECT);
    }

    public boolean isConnected() {
        return (mState == STATE_CONNECTED);
    }

    public synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mCommunicateThread != null) {
            mCommunicateThread.cancel();
            mCommunicateThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        mState = STATE_NONE;
    }

    public void write(byte[] data) {
        CommunicateThread communicateThread;
        synchronized (this) {
            if (!this.isConnected()) {
                return;
            }

            communicateThread = mCommunicateThread;
        }
        communicateThread.write(data);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        //Send a failure message back to the Activity
        mDisconnectReason = "Unable to connect device";
        mUiHandler.sendEmptyMessage(MSG_ON_DISCONNECT);

        mState = STATE_NONE;

        //Start the service over to restart listening mode
        this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        mDisconnectReason = "Device connection was lost";
        mUiHandler.sendEmptyMessage(MSG_ON_DISCONNECT);

        mState = STATE_NONE;

        //Start the service over to restart listening mode
        this.start();
    }

    private class AcceptThread extends Thread {
        private final String CLASS_NAME = AcceptThread.class.getSimpleName();
        private BluetoothServerSocket mBluetoothServerSocket;
        private String mSocketType;

        AcceptThread(boolean secure) {
            mSocketType = secure ? "Secure" : "Insecure";
            try {
                if (secure) {
                    mBluetoothServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                            NAME_SECURE, UUID_SECURE);
                } else {
                    mBluetoothServerSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", listen failed", e);
            }
            mState = STATE_LISTEN;
        }

        @Override
        public void run() {
            Log.d(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", accept begin");
            setName(CLASS_NAME + mSocketType);

            BluetoothSocket bluetoothSocket;

            // Listen to the server socket if we're not connected
            while (!isConnected()) {
                try {
                    // This is a blocking call and will only return on a successful connection or an exception
                    bluetoothSocket = mBluetoothServerSocket.accept();
                    if (mRemoteDevice == null) {
                        mRemoteDevice = bluetoothSocket.getRemoteDevice();
                    }
                    Log.d(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", accept: " +
                            mRemoteDevice.getName() + ", address: " + mRemoteDevice.getAddress());
                } catch (IOException e) {
                    Log.e(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", accept failed", e);
                    break;
                }

                // If a connection was accepted
                synchronized (ConnectHelper.this) {
                    switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(bluetoothSocket, mSocketType);
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                bluetoothSocket.close();
                            } catch (IOException e) {
                                Log.e(TAG, CLASS_NAME + " could not close unwanted socket", e);
                            }
                            break;
                    }
                }

            }
            Log.d(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", accept end");
        }

        private void cancel() {
            try {
                mBluetoothServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", close BluetoothServerSocket failed", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final String CLASS_NAME = ConnectThread.class.getSimpleName();
        private BluetoothSocket mBluetoothSocket;
        private String mSocketType;

        ConnectThread(BluetoothDevice device, boolean secure) {
            mSocketType = secure ? "Secure" : "Insecure";
            try {
                if (secure) {
                    mBluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_SECURE);
                } else {
                    mBluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(UUID_INSECURE);
                }
                mState = STATE_CONNECTING;
            } catch (Exception e) {
                Log.e(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", create failed", e);
            }
        }

        @Override
        public void run() {
            Log.d(TAG, CLASS_NAME + " SocketType:" + mSocketType + ", connect begin");
            setName(CLASS_NAME + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a successful connection or an exception
                mBluetoothSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " connection failed ", e);
                try {
                    mBluetoothSocket.close();// Close the socket
                } catch (Exception e2) {
                    e2.printStackTrace();
                }

                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (ConnectHelper.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mBluetoothSocket, mSocketType);
            Log.d(TAG, CLASS_NAME + " SocketType:" + mSocketType + ", connect end");
        }

        private void cancel() {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + "close connect " + mSocketType + " socket failed", e);
            }
        }
    }

    private class CommunicateThread extends Thread {
        private final String CLASS_NAME = CommunicateThread.class.getSimpleName();
        private BluetoothSocket mBluetoothSocket;
        private InputStream mInputStream;
        private OutputStream mOutputStream;

        CommunicateThread(BluetoothSocket socket) {
            mBluetoothSocket = socket;
            try {
                mInputStream = socket.getInputStream();
                mOutputStream = socket.getOutputStream();
                mState = STATE_CONNECTED;
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " BluetoothSocket created failed", e);
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[2 * 1024];
            byte[] data;
            while (isConnected()) {
                try {
                    data = new byte[mInputStream.read(buffer)];
                    Log.d(TAG, "read data len: " + data.length);
                    System.arraycopy(buffer, 0, data, 0, data.length);
                    onReceiveDataResponse(data);
                } catch (Exception e) {
                    Log.e(TAG, CLASS_NAME + " read data failed", e);
                    connectionLost();
                    break;
                }
            }
        }

        private void write(byte[] data) {
            try {
                mOutputStream.write(data);
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " write data failed", e);
            }
        }

        private void cancel() {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " close BluetoothSocket failed", e);
            }
        }
    }

    private void onConnectResponse() {
        if (mOnConnectListener != null) {
            mOnConnectListener.onConnect(mRemoteDevice);
        }
    }

    private void onDisconnectResponse() {
        if (mOnConnectListener != null) {
            mOnConnectListener.onDisconnect(mRemoteDevice, mDisconnectReason);
        }
    }

    private void onReceiveDataResponse(byte[] data) {
        if (mOnConnectListener != null) {
            mOnConnectListener.onReceiveData(data);
        }
    }

    public void setOnConnectListener(OnConnectListener onConnectListener) {
        mOnConnectListener = onConnectListener;
    }

    private static class UiHandler extends Handler {
        private WeakReference<ConnectHelper> mWeakReference;

        UiHandler(ConnectHelper connectHelper) {
            mWeakReference = new WeakReference<>(connectHelper);
        }

        @Override
        public void dispatchMessage(@NonNull Message msg) {
            super.dispatchMessage(msg);
            ConnectHelper connectHelper = mWeakReference.get();
            if (connectHelper == null) {
                return;
            }

            switch (msg.what) {
                case MSG_ON_CONNECT:
                    connectHelper.onConnectResponse();
                    break;
                case MSG_ON_DISCONNECT:
                    connectHelper.onDisconnectResponse();
                    break;
                default:
                    break;
            }
        }
    }

}
