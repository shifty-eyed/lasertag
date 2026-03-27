#pragma once

#include "definitions.h"

class MessageDebouncer {

 public:
  MessageDebouncer() {
    for (size_t i = 0; i < size; i++) {
      lastUsedTime[i] = 0;
    }
  }

  bool allow(int8_t messageType, int8_t counterpartPlayerId) {
    const uint32_t nowMs = millis();
    int8_t key = getKey(messageType, counterpartPlayerId);
    if (key == -1) {
      return true;
    }
    if (nowMs - lastUsedTime[key] < IR_DEBOUNCE_WINDOW_MS) {
      return false;
    }
    lastUsedTime[key] = nowMs;
    return true;
  }

  private:
    static constexpr size_t size = MAX_PLAYERS + 6;
    uint32_t lastUsedTime[size];

    static inline int8_t getKey(uint8_t type, uint8_t payload) {
      switch (type) {
        case MSG_TYPE_VEST_HIT: return payload < MAX_PLAYERS ? payload : -1;
        case MSG_TYPE_RESPAWN: return MAX_PLAYERS + 1;
        case MSG_TYPE_GOT_AMMO: return MAX_PLAYERS + 2;
        case MSG_TYPE_GOT_HEALTH: return MAX_PLAYERS + 3;
        case MSG_TYPE_FLAG: return MAX_PLAYERS + 4;
        case MSG_TYPE_GUN_RELOAD: return MAX_PLAYERS + 5;
        default: return -1;
      }
    }
};

