package com.bryanplant.flutterrc.flutterrc;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import android.content.Intent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@TargetApi(23)
public class MainActivity extends FlutterActivity {
    //channel for flutter code to access
    private static final String CHANNEL = "com.bryanplant/bluetooth";
    int REQUEST_ENABLE_BT = 0;
    private final String UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB";
    UUID myUUID = UUID.fromString(UUID_STRING);

    BluetoothThread bluetoothThread;    //used to connect to bluetooth device
    ConnectedThread connectedThread;    //used to send and receive messages from connected device
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
    List<String> pairedNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //a method channel that lets gui make Java bluetooth calls
        new MethodChannel(getFlutterView(), CHANNEL).setMethodCallHandler(new MethodCallHandler() {
            @Override
            public void onMethodCall(MethodCall call, Result result) {
                switch (call.method) {
                    //returns a list of all paired devices
                    case "init":
                        if (!mBluetoothAdapter.isEnabled()) {
                            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        }

                        if (pairedDevices.size() > 0) {
                            // There are paired devices. Get the name and address of each paired device.
                            for (BluetoothDevice device : pairedDevices) {
                                    String deviceName = device.getName();
                                    pairedNames.add(deviceName);
                                //String deviceHardwareAddress = device.getAddress(); // MAC address
                            }
                        }

                        result.success(pairedNames);
                        break;
                    //connect to a given bluetooth device
                    case "connect":
                        for (BluetoothDevice b : pairedDevices) {
                            if (b.getName().equals(call.arguments)) {
                                String address = b.getAddress();
                                bluetoothThread = new BluetoothThread(address);
                                bluetoothThread.start();
                            }
                        }
                        result.success(true);
                        break;
                    //send a message to bluetooth device
                    case "write":
                        connectedThread.write(call.arguments.toString());
                        break;
                    default:
                        result.notImplemented();
                        break;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(bluetoothThread != null){
            bluetoothThread.cancel();
        }
    }

    private void startBluetoothThread(BluetoothSocket socket) {
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    //Thread for connecting to a bluetooth device
    class BluetoothThread extends Thread {
        private BluetoothSocket bluetoothSocket = null;
        private BluetoothDevice bluetoothDevice;

        BluetoothThread(String s) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothDevice = adapter.getRemoteDevice(s);

            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(myUUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                bluetoothSocket.connect();
                success = true;
            } catch (IOException e) {
                e.printStackTrace();

                try {
                    bluetoothSocket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if(success){
                System.out.println("Connected!");
                startBluetoothThread(bluetoothSocket);
            }
        }

        void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Thread to handle data communication after connected
    private class ConnectedThread extends Thread {
        private final BluetoothSocket connectedBluetoothSocket;
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;

        ConnectedThread(BluetoothSocket socket) {
            connectedBluetoothSocket = socket;
            InputStream in = null;
            OutputStream out = null;

            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            connectedInputStream = in;
            connectedOutputStream = out;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = connectedInputStream.read(buffer);
                    String strReceived = new String(buffer, 0, bytes);
                    final String msgReceived = String.valueOf(bytes) +
                            " bytes received:\n"
                            + strReceived;
                    System.out.println(msgReceived);

                } catch (IOException e) {
                    e.printStackTrace();

                    final String msgConnectionLost = "Connection lost:\n"
                            + e.getMessage();
                    runOnUiThread(new Runnable(){

                        @Override
                        public void run() {

                        }});
                }
            }
        }

        public void write(String data) {
            try {
                connectedBluetoothSocket.getOutputStream().write(data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                connectedBluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
