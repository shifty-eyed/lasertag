#include <Arduino.h>
#include <IRremote.hpp>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <WiFi.h>
#include <WiFiUdp.h>
#include "definitions.h"

WiFiUDP udp;
const IPAddress discoveryServerIp = IPAddress(255, 255, 255, 255);
IPAddress serverIp = discoveryServerIp;
const unsigned int serverUdpPort = 9878;
const unsigned int localUdpPort = 1234;
const char* ssid = "imenilenina-bistro";
const char* password = "10101010";

TaskHandle_t udpSendTaskHandle = NULL;
TaskHandle_t udpReceiveTaskHandle = NULL;


volatile uint32_t timeSinceLastPingReceived = 999;
volatile uint32_t timeSinceLastPingSent = 0;
volatile uint32_t dispenseTimeoutSec = 30;
volatile uint32_t timeSinceLastDispense = dispenseTimeoutSec;

#define PING_EVERY_SECONDS 3
#define IR_SIGNAL_EVERY_MILLIS 2000

bool isOnline() {
  return timeSinceLastPingReceived < 10;
}


void taskStatusLed(void *pvParameters) {
  while (1) {
    if (!isOnline()) {
      digitalWrite(CONNECTION_STATUS_LED, HIGH);
      vTaskDelay(500 / portTICK_PERIOD_MS);
      digitalWrite(CONNECTION_STATUS_LED, LOW);
      vTaskDelay(500 / portTICK_PERIOD_MS);
    }
    vTaskDelay(50 / portTICK_PERIOD_MS);
  }
}

void connectToWiFi();
void taskTimerTick(void* pvParameters);
void taskUdpReceiver(void* pvParameters);
void sendUdpPingMessage();

void setup() {
  Serial.begin(9600);
  IrSender.begin(IR_LED_PIN);

  pinMode(CONNECTION_STATUS_LED, OUTPUT);
  pinMode(MAIN_LIGHT_LED, OUTPUT);
 
  Serial.println("Ready");

  xTaskCreate(taskStatusLed, "taskStatusLed", 2048, NULL, 1, NULL);

  connectToWiFi();
  udp.begin(localUdpPort);
  Serial.println("UDP started on port: " + String(localUdpPort));
  xTaskCreate(taskTimerTick, "taskTimerTick", 2048, NULL, 1, &udpSendTaskHandle);
  xTaskCreate(taskUdpReceiver, "taskUdpReceiver", 2048, NULL, 1, &udpReceiveTaskHandle);
}

void loop() {

  if (isOnline() && timeSinceLastDispense > dispenseTimeoutSec) {
    IrSender.sendSony(IR_ADDRESS, DEVICE_ID, 2, SIRCS_12_PROTOCOL);
    digitalWrite(MAIN_LIGHT_LED, HIGH);
    vTaskDelay(IR_SIGNAL_EVERY_MILLIS / portTICK_PERIOD_MS);
  } else {
    digitalWrite(MAIN_LIGHT_LED, LOW);
  }
  
  vTaskDelay(10 / portTICK_PERIOD_MS);
}

void taskTimerTick(void* pvParameters) {
  while (1) {
    if (timeSinceLastPingSent > PING_EVERY_SECONDS) {
      sendUdpPingMessage();
      timeSinceLastPingSent = 0;
    }
    vTaskDelay(1000 / portTICK_PERIOD_MS);
    timeSinceLastPingReceived ++;
    timeSinceLastPingSent ++;
    timeSinceLastDispense ++;
  }
  vTaskDelete(NULL);
}

void connectToWiFi() {
  Serial.println("Connecting to WiFi...");
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    vTaskDelay(2000 / portTICK_PERIOD_MS);
    Serial.println("Connecting...");
  }

  Serial.println("Connected to WiFi.");
  Serial.print("Local IP Address: ");
  Serial.println(WiFi.localIP());
}

void sendUdpPingMessage() {
  if (WiFi.status() != WL_CONNECTED) {
    return;
  }
  udp.beginPacket(serverIp, serverUdpPort);
  udp.write(MSG_TYPE_PING);
  udp.write(DEVICE_ID);
  udp.endPacket();
}

void taskUdpReceiver(void* pvParameters) {
  char incomingPacket[16];
  while (1) {
    int packetSize = udp.parsePacket();
    if (packetSize) {
      serverIp = udp.remoteIP();
      int len = udp.read(incomingPacket, sizeof(incomingPacket));
      if (len == 0) {
        Serial.println("Received Empty message");
        continue;
      }
      timeSinceLastPingReceived = 0;
      digitalWrite(CONNECTION_STATUS_LED, HIGH);

      int8_t type = incomingPacket[0];
      if (type == MSG_TYPE_SERVER_PING) {
        //Serial.printf("Received Ping ACK: %d\n", incomingPacket[0]);
      } else if (type == MSG_TYPE_IN_DISPENSER_USED) {
        timeSinceLastDispense = 0;
        digitalWrite(MAIN_LIGHT_LED, LOW);
        Serial.println("Received MSG_TYPE_IN_DISPENSER_USED");
      } else if (type == MSG_TYPE_IN_DISPENSER_SET_TIMEOUT) {
        if (len < 2) {
          Serial.println("Invalid MSG_TYPE_IN_DISPENSER_SET_TIMEOUT message length");
          continue;
        }
        dispenseTimeoutSec = incomingPacket[1] * 10; // with 10 sec steps
        Serial.printf("Received DISPENSER_SET_TIMEOUT: %d\n", dispenseTimeoutSec);
      }
    }
    vTaskDelay(20 / portTICK_PERIOD_MS);
  }
}