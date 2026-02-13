package net.lasertag.communication;

import net.lasertag.model.WirelessMessage;

@FunctionalInterface
public interface WirelessMessageHandler {

    void handleWirelessEvent(WirelessMessage message);
}
