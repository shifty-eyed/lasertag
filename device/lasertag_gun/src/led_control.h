#pragma once

#include <Arduino.h>
#include "definitions.h"

#include "freertos/task.h"

#define STATUS_LED_RED 33
#define STATUS_LED_GREEN 32
#define STATUS_LED_BLUE 25
#define STATUS_LED_ONBOARD 2
#define STATUS_LED_GUN 26

#define STATUS_LED_PWM_FREQ_HZ 5000
#define STATUS_LED_PWM_RESOLUTION_BITS 8
#define STATUS_LED_PWM_MAX_DUTY ((1 << STATUS_LED_PWM_RESOLUTION_BITS) - 1)

#define STATUS_LED_RED_CHANNEL 0
#define STATUS_LED_GREEN_CHANNEL 1
#define STATUS_LED_BLUE_CHANNEL 2

#ifdef VEST
#define ON_LEVEL_NORMAL 20
#else
#define ON_LEVEL_NORMAL 255
#endif


static int8_t statusLedChannelForPin(uint8_t pin) {
  switch (pin) {
    case STATUS_LED_RED:
      return STATUS_LED_RED_CHANNEL;
    case STATUS_LED_GREEN:
      return STATUS_LED_GREEN_CHANNEL;
    case STATUS_LED_BLUE:
      return STATUS_LED_BLUE_CHANNEL;
    default:
      return -1;
  }
}

static void initStatusLedPwm() {
  pinMode(STATUS_LED_ONBOARD, OUTPUT);
  pinMode(STATUS_LED_GUN, OUTPUT);
  ledcSetup(STATUS_LED_RED_CHANNEL, STATUS_LED_PWM_FREQ_HZ, STATUS_LED_PWM_RESOLUTION_BITS);
  ledcSetup(STATUS_LED_GREEN_CHANNEL, STATUS_LED_PWM_FREQ_HZ, STATUS_LED_PWM_RESOLUTION_BITS);
  ledcSetup(STATUS_LED_BLUE_CHANNEL, STATUS_LED_PWM_FREQ_HZ, STATUS_LED_PWM_RESOLUTION_BITS);

  ledcAttachPin(STATUS_LED_RED, STATUS_LED_RED_CHANNEL);
  ledcAttachPin(STATUS_LED_GREEN, STATUS_LED_GREEN_CHANNEL);
  ledcAttachPin(STATUS_LED_BLUE, STATUS_LED_BLUE_CHANNEL);
}

static void colorLedOn(uint8_t pin, uint8_t brightness) {
  int8_t channel = statusLedChannelForPin(pin);
  if (channel < 0) {
    return;
  }
  uint8_t clamped = brightness > STATUS_LED_PWM_MAX_DUTY ? STATUS_LED_PWM_MAX_DUTY : brightness;
  #ifdef VEST
  uint8_t duty = clamped;
  #else
  uint8_t duty = STATUS_LED_PWM_MAX_DUTY - clamped; //gun (active low)
  #endif
  ledcWrite(channel, duty);
}

static void colorLedsOff() {
  colorLedOn(STATUS_LED_GREEN, 0);
  colorLedOn(STATUS_LED_BLUE, 0);
  colorLedOn(STATUS_LED_RED, 0);
}

void ledControlCycle(bool isOnline, int8_t playerTeam, int8_t playerState) {
    colorLedsOff();
    if (!isOnline) {
        digitalWrite(STATUS_LED_GUN, HIGH);
        digitalWrite(STATUS_LED_ONBOARD, HIGH);
        vTaskDelay(500);
        digitalWrite(STATUS_LED_GUN, LOW);
        digitalWrite(STATUS_LED_ONBOARD, LOW);
        vTaskDelay(500);
    } else {
        switch (playerTeam) {
        case TEAM_RED: 
            colorLedOn(STATUS_LED_RED, ON_LEVEL_NORMAL);
            break;
        case TEAM_BLUE:
            colorLedOn(STATUS_LED_BLUE, ON_LEVEL_NORMAL);
            break;
        case TEAM_GREEN:
            colorLedOn(STATUS_LED_GREEN, ON_LEVEL_NORMAL);
            break;
        case TEAM_YELLOW:
            colorLedOn(STATUS_LED_RED, ON_LEVEL_NORMAL);
            colorLedOn(STATUS_LED_GREEN, ON_LEVEL_NORMAL);
            break;
        case TEAM_MAGENTA:
            colorLedOn(STATUS_LED_RED, ON_LEVEL_NORMAL);
            colorLedOn(STATUS_LED_BLUE, ON_LEVEL_NORMAL);
            break;
        case TEAM_CYAN:
            colorLedOn(STATUS_LED_BLUE, ON_LEVEL_NORMAL);
            colorLedOn(STATUS_LED_GREEN, ON_LEVEL_NORMAL);
            break;
        }
        if (playerState == PLATER_STATE_PLAY) {
        vTaskDelay(1000);
        } else {
        vTaskDelay(30);
        colorLedsOff();
        vTaskDelay(2000);
        }
    }
}
