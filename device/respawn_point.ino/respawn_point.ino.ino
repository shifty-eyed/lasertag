// Respawn Point IR Beacon for Laser Tag System
// ---------------------------------------------
// * Hardware: Arduino Nano + IR LED on pin 3 (PWM capable)
// * Library : Arduino-IRremote (v4.x or newer)
//
// The beacon transmits a Sony 12‑bit IR frame once per second.
//   • Address  = IR_ADDRESS_RESPAWN (fixed at 1 for all beacons)
//   • Command  = DEVICE_ID (unique per beacon, 0‑127)
//
// Players’ vests listen for Address = 1 and use the command value to
// identify which respawn point granted the event.
// -------------------------------------------------------------

#include <IRremote.hpp>


constexpr uint8_t DEVICE_ID            = 0;

constexpr uint8_t IR_ADDRESS_RESPAWN   = 1; // never changes


constexpr uint8_t PIN_IR_LED           = 3;   // PWM‑capable pin on Nano
constexpr uint32_t TX_INTERVAL_MS      = 200;


constexpr uint8_t PIN_STATUS_LED       = LED_BUILTIN;



void setup() {
  pinMode(PIN_STATUS_LED, OUTPUT);
  IrSender.begin(PIN_IR_LED, false, 0);
}

void loop() {
  digitalWrite(PIN_STATUS_LED, HIGH);
  delay(10);
  digitalWrite(PIN_STATUS_LED, LOW);
  
  IrSender.sendSony(IR_ADDRESS_RESPAWN, DEVICE_ID, 4, SIRCS_12_PROTOCOL);
  delay(TX_INTERVAL_MS);
}
