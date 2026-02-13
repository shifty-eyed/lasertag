package net.lasertag.communication;

import static net.lasertag.Config.SERVICE_UUID;
import static net.lasertag.Config.TAG;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

@SuppressLint("MissingPermission")
public class BluetoothClient extends SerialClient {

    public static final byte DEVICE_GUN = 1;
    public static final byte DEVICE_VEST = 2;
    public static final byte DEVICE_DEBUG = 3;

    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice espDevice;

    public BluetoothClient(String deviceName, int deviceType, BluetoothAdapter bluetoothAdapter, WirelessMessageHandler messageHandler) {
        super(deviceName, deviceType, messageHandler);
        this.bluetoothAdapter = bluetoothAdapter;
    }

    @Override
    protected boolean connectToDevice() {
        Log.i(TAG, "Connecting to Bluetooth device " + deviceName);
        if (!bluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Bluetooth is not enabled");
            return false;
        }
        if (espDevice == null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (deviceName.equals(device.getName())) {
                    Log.i(TAG, "Found: " + device.getName());
                    espDevice = device;
                    break;
                }
            }
        }

        if (espDevice == null) {
            Log.i(TAG, "Device not found");
            return false;
        }

        try {
            bluetoothSocket = espDevice.createRfcommSocketToServiceRecord(SERVICE_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            Log.i(TAG, "Connected to device: " + deviceName);
        } catch (IOException e) {
            Log.w(TAG, "Failed to connect to ESP32 device: " + deviceName);
            return false;
        }
        return true;
    }

    @Override
    protected void closeConnection() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException ignored) {}
    }
}
