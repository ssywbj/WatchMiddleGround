package com.zhipu.middleground.app.connect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothConnectHelper {
    private static final String TAG = BluetoothConnectHelper.class.getSimpleName();

    private static final String NAME_SECURE = "BluetoothConnectSecure";
    private static final String NAME_INSECURE = "BluetoothConnectInsecure";

    private static final UUID UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    //private static final UUID UUID_INSECURE = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb");

    public static final int STATE_NONE = 0;
    private static final int STATE_LISTEN = 1;
    private static final int STATE_CONNECTING = 2;
    public static final int STATE_COMMUNICATE = 3;
    private int mState = STATE_NONE;

    private final BluetoothAdapter mBluetoothAdapter;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private CommunicateThread mConnectedThread;

    public BluetoothConnectHelper() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
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

    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
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
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
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
        mConnectedThread = new CommunicateThread(socket);
        mConnectedThread.start();
    }

    public synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
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

    /**
     * Write to the ConnectedThread in an not synchronized manner
     *
     * @param data The data to write
     * @see CommunicateThread#write(byte[])
     */
    public void write(byte[] data) {
        // Create temporary object
        CommunicateThread connectedThread;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_COMMUNICATE) {
                return;
            }

            connectedThread = mConnectedThread;
        }
        // Perform the write not synchronized
        connectedThread.write(data);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        mState = STATE_NONE;
        // Start the service over to restart listening mode
        this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        mState = STATE_NONE;
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
            while (mState != STATE_COMMUNICATE) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    bluetoothSocket = mBluetoothServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, CLASS_NAME + " Socket Type: " + mSocketType + ", accept failed", e);
                    break;
                }

                // If a connection was accepted
                if (bluetoothSocket != null) {
                    synchronized (BluetoothConnectHelper.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(bluetoothSocket, mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_COMMUNICATE:
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
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mBluetoothSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " connection failed ", e);
                // Close the socket
                try {
                    mBluetoothSocket.close();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }

                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothConnectHelper.this) {
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
                mState = STATE_COMMUNICATE;
            } catch (IOException e) {
                Log.e(TAG, CLASS_NAME + " BluetoothSocket created failed", e);
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[10 * 1024];
            int len;
            while (mState == STATE_COMMUNICATE) {
                try {
                    len = mInputStream.read(buffer);

                    String receive = new String(buffer, 0, len);
                    Log.d(TAG, CLASS_NAME + " read data: " + receive);

                    /*String response = "receive data: " + receive;
                    BluetoothConnectHelper.this.write(response.getBytes());*/
                } catch (IOException e) {
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

}
