#include <Arduino.h>
#include <IRremote.hpp>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "definitions.h"
#include "led_control.h"
#include <BluetoothSerial.h>

BluetoothSerial SerialBT;
TaskHandle_t udpSendTaskHandle = NULL;
TaskHandle_t udpReceiveTaskHandle = NULL;

static volatile uint32_t pingsSentWithNoResponse = 5;
static volatile int8_t playerId = 0;
static volatile int8_t playerTeam = TEAM_GREEN;
static volatile int8_t playerState = PLATER_STATE_IDLE;
static volatile int8_t bulletsLeft = 0;

void taskHeartbeat(void* pvParameters);
void taskInboundReceiverFromHost(void* pvParameters);
void taskIRReceiver(void* pvParameters);
void sendMessageToHost(int8_t messageType, int8_t counterpartPlayerId);
#if WIRING_MODE == WIRING_MODE_WIRED && defined(VEST)
void taskWiredReceiverFromGun(void* pvParameters);
#endif

static bool isOnline() {
  return pingsSentWithNoResponse < 3;
}

void taskStatusLed(void *pvParameters) {
  while (1) {
    ledControlCycle(isOnline(), playerTeam, playerState);
  }
}

void setup() {
  Serial.begin(9600);
  IrSender.begin(IR_LED_PIN);
  IrReceiver.begin(IR_RECEIVER_PIN);

#if WIRING_MODE == WIRING_MODE_WIRED
  Serial2.begin(9600, SERIAL_8N1, WIRED_UART_RX_PIN, WIRED_UART_TX_PIN);
#endif

  pinMode(FIRE_PIN, INPUT_PULLUP);
  pinMode(RELOAD_PIN, INPUT_PULLUP);
  initStatusLedPwm();
  xTaskCreate(taskStatusLed, "taskStatusLed", 2048, NULL, 1, NULL);

#if WIRING_MODE == WIRING_MODE_WIRELESS || defined(VEST)
  if (!SerialBT.begin(deviceName)) {
    Serial.println("An error occurred initializing Bluetooth");
  } else {
    while (!SerialBT.connected(2000)) {
      Serial.println("Connecting BT..");
      vTaskDelay(2000 / portTICK_PERIOD_MS);
    }
    Serial.println("BT connected.");
  }
#endif
  
  xTaskCreate(taskHeartbeat, "taskHeartbeat", 2048, NULL, 1, &udpSendTaskHandle);
  xTaskCreate(taskInboundReceiverFromHost, "taskInboundReceiver", 2048, NULL, 1, &udpReceiveTaskHandle);
  xTaskCreate(taskIRReceiver, "taskIRReceiver", 2048, NULL, 1, &udpReceiveTaskHandle);
#if WIRING_MODE == WIRING_MODE_WIRED && defined(VEST)
  xTaskCreate(taskWiredReceiverFromGun, "taskWiredReceiver", 2048, NULL, 1, &udpReceiveTaskHandle);
#endif
}

void loop() {

  int fireButtonState = digitalRead(FIRE_PIN);
  int reloadButtonState = digitalRead(RELOAD_PIN);

  if (fireButtonState == LOW && isOnline()) {
    if (bulletsLeft > 0 && playerId != 0 && playerState == PLATER_STATE_PLAY) {
      IrSender.sendSony(IR_ADDRESS_GUN, playerId, 1, SIRCS_12_PROTOCOL);
    }
    sendMessageToHost(MSG_TYPE_GUN_SHOT, 0);
    vTaskDelay(GUN_FIRE_INTERVAL / portTICK_PERIOD_MS);
  }

  if (reloadButtonState == LOW) {
    Serial.println("Reload");
    sendMessageToHost(MSG_TYPE_GUN_RELOAD, 0);
    vTaskDelay(2000 / portTICK_PERIOD_MS);
  }
  vTaskDelay(10 / portTICK_PERIOD_MS);
}


void taskIRReceiver(void *pvParameters) {
  uint32_t playersHitRecentHit[MAX_PLAYERS];
  for (int i = 0; i < MAX_PLAYERS; i++) {
    playersHitRecentHit[i] = 0;
  }

  while (1) {
    if (IrReceiver.decode()) {
      uint8_t address = (uint8_t)IrReceiver.decodedIRData.address;
      uint8_t command = (uint8_t)IrReceiver.decodedIRData.command;
      //Serial.println("IR received. Address: " + String(address) + ", Command: " + String(command));
      if (address == IR_ADDRESS_GUN) {
        uint8_t hitByPlayer = command;
        if (hitByPlayer != playerId 
          && (millis() - playersHitRecentHit[hitByPlayer] > GUN_FIRE_INTERVAL)) {
          playersHitRecentHit[hitByPlayer] = millis();
          Serial.println("Hit by player: " + String(hitByPlayer));
          sendMessageToHost(MSG_TYPE_VEST_HIT, hitByPlayer);
        } else {
          Serial.println("Hit by myself");
        }
      } else if (address == IR_ADDRESS_RESPAWN && playerState == PLATER_STATE_DEAD) {
        uint8_t respawnPointId = command;
        Serial.println("Respawn");
        sendMessageToHost(MSG_TYPE_RESPAWN, respawnPointId);
      } else if (address == IR_ADDRESS_AMMO && playerState == PLATER_STATE_PLAY) {
        Serial.println("Got Ammo");
        sendMessageToHost(MSG_TYPE_GOT_AMMO, command);
      } else if (address == IR_ADDRESS_HEALTH && playerState == PLATER_STATE_PLAY) {
        Serial.println("Got Health");
        sendMessageToHost(MSG_TYPE_GOT_HEALTH, command);
      } else if (address == IR_ADDRESS_FLAG && playerState == PLATER_STATE_PLAY) {
        Serial.println("Flag");
        sendMessageToHost(MSG_TYPE_FLAG, command);
      }
      IrReceiver.resume();
    }
    vTaskDelay(30 / portTICK_PERIOD_MS);
  }
}

// Host is either Phone over SerialBT or Vest esp32 over Serial2
static Stream &getHostStream() {
#if WIRING_MODE == WIRING_MODE_WIRED && defined(GUN)
  return Serial2;
#else
  return SerialBT;
#endif
}

void sendMessageToHost(int8_t messageType, int8_t counterpartPlayerId) {
  Stream &outbound = getHostStream();
  outbound.write(messageType);
  outbound.write(counterpartPlayerId);
  outbound.write(STOP_BYTE);
  outbound.flush();

  if (messageType != MSG_TYPE_PING) {
    Serial.println("Sent message: " + String(messageType));
  }
}

void sendCurrentStateToWiredGun() {
    Serial2.write(MSG_TYPE_IN_PLAYER_STATE);
    Serial2.write(playerId);
    Serial2.write(playerTeam);
    Serial2.write(playerState);
    Serial2.write(bulletsLeft);
    Serial2.write(STOP_BYTE);
    Serial2.flush();
}

void taskHeartbeat(void* pvParameters) {
  while (1) {
    if (pingsSentWithNoResponse < 10) {
      sendMessageToHost(MSG_TYPE_PING, 0);
      pingsSentWithNoResponse ++;
    }
#if WIRING_MODE == WIRING_MODE_WIRED && defined(VEST)
    sendCurrentStateToWiredGun();
#endif
    vTaskDelay(3000 / portTICK_PERIOD_MS);
  }
  vTaskDelete(NULL);
}

void taskInboundReceiverFromHost(void* pvParameters) {
  char incomingPacket[16];
  Stream &inbound = getHostStream();
  while (1) {
    if (inbound.available()) {
      int len = inbound.readBytesUntil(STOP_BYTE, incomingPacket, sizeof(incomingPacket));
      if (len == 0) {
        Serial.println("Received Empty message");
        continue;
      }
      pingsSentWithNoResponse = 0;
      int8_t type = incomingPacket[0];

      if (type == MSG_TYPE_PING) {
        Serial.println("PING from BT");
      } else if (type == MSG_TYPE_IN_PLAYER_STATE) {
        if (len < 3) {
          Serial.println("Invalid player state message length");
          continue;
        }
        playerId = incomingPacket[1];
        playerTeam = incomingPacket[2];
        playerState = incomingPacket[3];
        bulletsLeft = incomingPacket[4];
        Serial.printf("Player Info Update: playerId=%d, team=%d, state=%d, bullets=%d\n", 
          playerId, playerTeam, playerState, bulletsLeft);
#if WIRING_MODE == WIRING_MODE_WIRED && defined(VEST)
        sendCurrentStateToWiredGun();
        //Serial.printf("FWD to GUN: type=%d\n", type);
#endif
      }
    }
    vTaskDelay(20 / portTICK_PERIOD_MS);
  }
}

#if WIRING_MODE == WIRING_MODE_WIRED && defined(VEST)
void taskWiredReceiverFromGun(void* pvParameters) {
  char incomingPacket[8];
  while (1) {
    if (Serial2.available()) {
      int len = Serial2.readBytesUntil(STOP_BYTE, incomingPacket, sizeof(incomingPacket));
      if (len != 2) {
        Serial.println("Corrupted wired message - ignored");
        continue;
      }

      //Serial.printf("WIRE len=%d type=%d\n", len, (int)incomingPacket[0]);
      if (incomingPacket[0] == MSG_TYPE_PING) {
        Serial.println("WIRED: PING from GUN");
        continue;
      }
      sendMessageToHost(incomingPacket[0], incomingPacket[1]);
      Serial.printf("FWD to BT: type=%d\n", incomingPacket[0]);
    }
    vTaskDelay(20 / portTICK_PERIOD_MS);
  }
}
#endif
