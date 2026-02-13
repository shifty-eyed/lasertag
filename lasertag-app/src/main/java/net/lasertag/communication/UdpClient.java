package net.lasertag.communication;

import static net.lasertag.Config.HEARTBEAT_INTERVAL;
import static net.lasertag.Config.HEARTBEAT_TIMEOUT;
import static net.lasertag.Config.LISTENING_PORT;
import static net.lasertag.Config.SERVER_PORT;
import static net.lasertag.Config.TAG;

import android.util.Log;

import net.lasertag.Config;
import net.lasertag.model.EventMessageToServer;
import net.lasertag.model.SignalMessage;
import net.lasertag.model.Messaging;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class UdpClient {

    private final Config config;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
    private final DatagramSocket serverSocket;
    private volatile boolean isOnline = false;
    private volatile long lastPingTime = 0L;
    private boolean firstEverMessage;
    private final WirelessMessageHandler messageHandler;
    private volatile boolean running = false;

    public UdpClient(Config config, WirelessMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        this.config = config;
        running = true;
        firstEverMessage = true;
        try {
            serverSocket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException("Failed to create UDP socket", e);
        }
        executorService.scheduleWithFixedDelay(this::heartbeat, 0, HEARTBEAT_INTERVAL, java.util.concurrent.TimeUnit.MILLISECONDS);
        executorService.execute(this::loop);
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        executorService.shutdown();
    }

    public void sendEventToServer(EventMessageToServer message) {
        try {
            var ip = config.getServerAddress() == null ? config.getBroadcastAddress() : config.getServerAddress();
            var rawMessage = message.getBytes();
            DatagramPacket packet = new DatagramPacket(rawMessage, rawMessage.length, ip, SERVER_PORT);
            serverSocket.send(packet);
            Log.i(TAG, "Sent to server: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send event to server", e);
        }
    }

    private void heartbeat() {
        if (System.currentTimeMillis() - lastPingTime > HEARTBEAT_TIMEOUT) {
            Log.i(TAG, "Connection timeout.");
            isOnline = false;
            messageHandler.handleWirelessEvent(new SignalMessage(Messaging.SERVER_DISCONNECTED));
        }
        try {
            byte[] message = new byte[] { Messaging.PLAYER_REPLY_PING, config.getPlayerId(), firstEverMessage ? (byte) 1 : (byte) 0 };
            var ip = config.getServerAddress() == null ? config.getBroadcastAddress() : config.getServerAddress();
            DatagramPacket packet = new DatagramPacket(message, 3, ip, SERVER_PORT);
            serverSocket.send(packet);
            firstEverMessage = false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    private void loop() {
        while (running) {
            try (var socket = new DatagramSocket(LISTENING_PORT)) {
                var buffer = new byte[512];
                Log.i(TAG, "Listening on socket: " + socket.getLocalSocketAddress());
                while (running) {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    var message = Messaging.fromBytes(packet.getData(), packet.getLength());
                    isOnline = true;
                    if (config.getServerAddress() == null) {
                        Log.i(TAG, "Server IP discovered: " + packet.getAddress());
                        config.setServerAddress(packet.getAddress());
                    }
                    lastPingTime = System.currentTimeMillis();
                    if (message.getType() != Messaging.PING) {
                        messageHandler.handleWirelessEvent(message);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to receive UDP message", e);
                Thread.yield();
            }
        }
    }
}
