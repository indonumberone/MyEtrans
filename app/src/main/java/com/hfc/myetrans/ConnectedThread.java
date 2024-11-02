package com.hfc.myetrans;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final Handler handler;
    private static final String TAG = ConnectedThread.class.getSimpleName();

    public ConnectedThread(BluetoothSocket socket, Handler handler) {
        mmSocket = socket;
        this.handler = handler;
        InputStream tmpIn = null;

        // Get the input stream from the Bluetooth socket
        try {
            tmpIn = mmSocket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating input stream", e);
        }

        mmInStream = tmpIn;
    }

    public void run() {
        byte[] buffer = new byte[1024]; // Buffer for incoming data
        int numBytes; // Number of bytes read

        // Keep listening to the InputStream until an exception occurs
        while (true) {
            try {
                // Read from the InputStream
                numBytes = mmInStream.read(buffer);
                String receivedData = new String(buffer, 0, numBytes);

                // Send the received data to the handler
                handler.obtainMessage(MainActivity.MESSAGE_READ, receivedData).sendToTarget();
            } catch (IOException e) {
                Log.d(TAG, "Input stream was disconnected", e);
                break;
            }
        }
    }

    // Call this method to cancel the connection
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}
