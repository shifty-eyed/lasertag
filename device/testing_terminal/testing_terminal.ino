#include <Arduino.h>
#include <IRremote.hpp>

constexpr uint8_t PIN_IR_RX = 2;
constexpr uint8_t PIN_IR_TX = 3;

constexpr uint8_t PIN_BTN_SHOT = 4;
constexpr uint8_t PIN_BTN_HEALTH = 5;
constexpr uint8_t PIN_BTN_AMMO = 6;

constexpr uint8_t PIN_STATUS_LED = LED_BUILTIN;

// Must match device/player_device/src/definitions.h
constexpr uint8_t IR_ADDRESS_RESPAWN = 1;
constexpr uint8_t IR_ADDRESS_HEALTH = 2;
constexpr uint8_t IR_ADDRESS_AMMO = 3;
constexpr uint8_t IR_ADDRESS_FLAG = 4;
constexpr uint8_t IR_ADDRESS_GUN = 5;

// Configure the command values you want to emulate.
constexpr uint8_t PLAYER_ID_SHOT = 0;
constexpr uint8_t HEALTH_DISPENSER_ID = 3;
constexpr uint8_t AMMO_DISPENSER_ID = 3;

constexpr uint16_t DEBOUNCE_MS = 30;

struct DebouncedButton {
  uint8_t pin;
  uint8_t lastReading;
  uint8_t stableState;
  uint32_t lastChangeMs;
};

static void blinkStatusLed(uint16_t ms = 20) {
  digitalWrite(PIN_STATUS_LED, HIGH);
  delay(ms);
  digitalWrite(PIN_STATUS_LED, LOW);
}

static bool pressedEdge(DebouncedButton &b, uint32_t nowMs) {
  uint8_t reading = (uint8_t)digitalRead(b.pin);
  if (reading != b.lastReading) {
    b.lastReading = reading;
    b.lastChangeMs = nowMs;
  }

  if ((nowMs - b.lastChangeMs) >= DEBOUNCE_MS && reading != b.stableState) {
    b.stableState = reading;
    return b.stableState == LOW;
  }

  return false;
}

static void printDecoded() {
  const uint8_t address = (uint8_t)IrReceiver.decodedIRData.address;
  const uint8_t command = (uint8_t)IrReceiver.decodedIRData.command;
  const uint32_t raw = (uint32_t)IrReceiver.decodedIRData.decodedRawData;

  Serial.print(F("IR rx: addr="));
  Serial.print(address);
  Serial.print(F(" cmd="));
  Serial.print(command);
  Serial.print(F(" raw=0x"));
  Serial.println(raw, HEX);
}

static void sendSony(uint8_t address, uint8_t command, uint8_t repeats) {
  IrSender.sendSony(address, command, repeats, SIRCS_12_PROTOCOL);
  Serial.print(F("IR send: addr="));
  Serial.print(address);
  Serial.print(F(" cmd="));
  Serial.print(command);
  Serial.print(F(" repeats="));
  Serial.println(repeats);
}

void setup() {
  pinMode(PIN_STATUS_LED, OUTPUT);
  digitalWrite(PIN_STATUS_LED, LOW);

  pinMode(PIN_BTN_SHOT, INPUT_PULLUP);
  pinMode(PIN_BTN_HEALTH, INPUT_PULLUP);
  pinMode(PIN_BTN_AMMO, INPUT_PULLUP);

  Serial.begin(115200);
  delay(200);

  IrSender.begin(PIN_IR_TX);
  IrReceiver.begin(PIN_IR_RX);

  Serial.println();
  Serial.println(F("LaserTag testing terminal ready"));
  Serial.print(F("IR RX pin="));
  Serial.print(PIN_IR_RX);
  Serial.print(F(" | IR TX pin="));
  Serial.println(PIN_IR_TX);
  Serial.println(F("Buttons (to GND, INPUT_PULLUP): D4=shot D5=health D6=ammo"));
  Serial.println(F("Send mapping:"));
  Serial.println(F(" - shot:   addr=5 (GUN)    cmd=0"));
  Serial.print(F(" - health: addr=2 (HEALTH) cmd="));
  Serial.println(HEALTH_DISPENSER_ID);
  Serial.print(F(" - ammo:   addr=3 (AMMO)   cmd="));
  Serial.println(AMMO_DISPENSER_ID);
}

void loop() {
  static DebouncedButton btnShot{PIN_BTN_SHOT, HIGH, HIGH, 0};
  static DebouncedButton btnHealth{PIN_BTN_HEALTH, HIGH, HIGH, 0};
  static DebouncedButton btnAmmo{PIN_BTN_AMMO, HIGH, HIGH, 0};

  if (IrReceiver.decode()) {
    blinkStatusLed(30);
    printDecoded();
    IrReceiver.resume();
  }

  const uint32_t nowMs = millis();
  if (pressedEdge(btnShot, nowMs)) {
    sendSony(IR_ADDRESS_GUN, PLAYER_ID_SHOT, 1);
  }
  if (pressedEdge(btnHealth, nowMs)) {
    sendSony(IR_ADDRESS_HEALTH, HEALTH_DISPENSER_ID, 2);
  }
  if (pressedEdge(btnAmmo, nowMs)) {
    sendSony(IR_ADDRESS_AMMO, AMMO_DISPENSER_ID, 2);
  }

  delay(5);
}
