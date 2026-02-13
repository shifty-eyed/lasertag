package net.lasertag.communication;

import static net.lasertag.Config.TAG;

import android.util.Log;

import net.lasertag.model.EventMessageIn;
import net.lasertag.model.SignalMessage;
import net.lasertag.model.WirelessMessage;
import net.lasertag.model.Messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SerialClient {

    protected static final byte STOP_BYTE = 125;

    protected final WirelessMessageHandler messageHandler;
    protected final String deviceName;
    protected final byte deviceType;

    protected final ExecutorService executorService = Executors.newSingleThreadExecutor();
    protected OutputStream outputStream;
    protected InputStream inputStream;
    protected volatile boolean running = false;

    public SerialClient(String deviceName, int deviceType, WirelessMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        this.deviceName = deviceName;
        this.deviceType = (byte) deviceType;
        running = true;
        executorService.execute(this::loop);
    }

    public void stop() {
        running = false;
        try {
            executorService.shutdown();
            closeConnection();
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            outputStream = null;
            inputStream = null;
        } catch (IOException ignored) {}
    }

    public void sendMessageToDevice(WirelessMessage message) {
        if (outputStream != null) {
            try {
                outputStream.write(message.getBytes());
                outputStream.write(STOP_BYTE);
                outputStream.flush();
                if (message.getType() != Messaging.PING) {
                    Log.i(TAG, "Sent to " + deviceName + ": " + message);
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to send message to " + deviceName);
            }
        }
    }

    private void loop() {
        while (running) {
            if (connectToDevice()) {
                messageHandler.handleWirelessEvent(new EventMessageIn(Messaging.DEVICE_CONNECTED, deviceType));
                listening();
            }
            Thread.yield();
        }
    }

    private void listening() {
        Log.i(TAG, "Listening to " + deviceName);
        List<Byte> buffer = new java.util.ArrayList<>(16);
        while (running) {
            try {
                buffer.clear();
                while (true) {
                    byte data = (byte) inputStream.read();
                    if (data == STOP_BYTE) {
                        break;
                    }
                    buffer.add(data);
                }
                if (!buffer.isEmpty()) {
                    if (buffer.size() != 2) {
                        Log.i(TAG, "Got something wrong: " + buffer);
                    } else if (buffer.get(0) == Messaging.PING) {
                        sendMessageToDevice(new SignalMessage());
                    } else {
                        Log.i(TAG, "Handling message: " + buffer);
                        messageHandler.handleWirelessEvent(Messaging.parseMessageFromDevice(buffer));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection lost", e);
                messageHandler.handleWirelessEvent(new EventMessageIn(Messaging.DEVICE_DISCONNECTED, deviceType));
                try {
                    closeConnection();
                    outputStream = null;
                    inputStream = null;
                } catch (Exception ignored) {}
                return;
            }
        }
    }

    protected abstract boolean connectToDevice();

    protected abstract void closeConnection();
}
