package net.lasertag.communication;

import net.lasertag.model.WirelessMessage;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class MockDeviceClient extends SerialClient {

    private PipedOutputStream pipedOutput;

    public MockDeviceClient(int deviceType, WirelessMessageHandler messageHandler) {
        super("DebugDevice", deviceType, messageHandler);
    }

    public void sendMessageBytes(byte[] bytes) throws IOException {
        pipedOutput.write(bytes);
        pipedOutput.write(SerialClient.STOP_BYTE);
        pipedOutput.flush();
    }

    @Override
    protected boolean connectToDevice() {
        try {
            pipedOutput = new PipedOutputStream();
            inputStream = new PipedInputStream(pipedOutput);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void closeConnection() {
        try {
            pipedOutput.close();
        } catch (Exception ignored) {}
    }

    @Override
    public void sendMessageToDevice(WirelessMessage message) {
    }
}
