# LaserTag Project Map

This repository is split into three main parts: Android client, embedded device projects, and backend server.

## Top-level

- `./android` - Android player app (`LasertagPlayer` / `:lasertag-app`)
- `./device` - firmware + hardware projects for ESP32-based game devices
- `./server` - Java Spring Boot game server

## `./android` (player app)

- Main mobile app used by players in the field.
- Connects to gear over Bluetooth (and supports mock/serial/UDP client code paths).
- Key app areas:
  - `android/lasertag-app/src/main/java/net/lasertag/` - app/game logic (`MainActivity`, `GameService`, config, sound)
  - `android/lasertag-app/src/main/java/net/lasertag/communication/` - transport clients (`BluetoothClient`, `SerialClient`, `UdpClient`, `MockDeviceClient`)
  - `android/lasertag-app/src/main/java/net/lasertag/model/` - messaging and domain models

## `./device` (microcontroller + hardware)

- Embedded projects for player equipment and field devices.
- Primary player gear is `device/player_device`:
  - ESP32 + Arduino framework (PlatformIO project)
  - Supports two wiring modes:
    - wireless mode: gun/vest communicate with phone over BT
    - wired mode: vest and gun connected over UART (`Serial2`) while vest still links to phone over BT
  - Used for both gun and vest builds via compile-time defines in `src/definitions.h`.
- Other device projects:
  - `device/dispenser` - ESP32 firmware for dispenser station: Health or Ammo
  - `device/respawn_point.ino` - Arduino sketch for Respawn point
  - `device/vest-v2` - KiCad hardware design files for vest electronics

## `./server` (backend)

- Spring Boot server (`net.lasertag:lasertag-server`, Java 21).
- Contains core game logic, UDP/game coordination, and web endpoints/UI assets.
- Key locations:
  - `server/src/main/java/net/lasertag/lasertagserver/core/` - game domain/runtime
  - `server/src/main/java/net/lasertag/lasertagserver/web/` - controllers + SSE/log streaming
  - `server/src/main/resources/static/` - simple web UI assets
  - `server/presets/` - game preset JSONs


## `./.context` (for AI agent context)

- contains up to date game description `game-description.md`
- `.context/plan` for implementation plans
- `.context/prompt` for temporary prepared prompts
