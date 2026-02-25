# Testing terminal wiring

Target: Arduino Nano / Uno (5V)

## Parts

- Arduino Nano/Uno (or compatible)
- IR receiver module (e.g. VS1838B / TSOP38xx type, 3-pin: `OUT`, `GND`, `VCC`)
- IR LED (940nm)
- 3x momentary push buttons
- Resistors:
  - 3x \(10k\) optional (only if you do NOT use `INPUT_PULLUP`)
  - IR LED series resistor: start with \(100–220\Omega\) (depends on drive method/current)
- Recommended for strong transmit range:
  - NPN transistor (e.g. 2N2222/BC337) or logic-level N-MOSFET
  - Base resistor ~\(1k\) (for NPN)

## Pin map (used by the sketch)

- **IR TX (IR LED drive)**: D3
- **IR RX (receiver OUT)**: D2
- **Button 1 (shot)**: D4
- **Button 2 (health)**: D5
- **Button 3 (ammo)**: D6
- **Status LED**: `LED_BUILTIN` (on-board)

Buttons are configured as `INPUT_PULLUP`:
- Wire **one side of each button to GND**
- Wire the **other side to its pin** (D4/D5/D6)

## IR receiver wiring

Most IR receiver modules are:

- `VCC` -> 5V (some modules also work on 3.3V; check your part)
- `GND` -> GND
- `OUT` -> D2

Keep the receiver wires short and add a 0.1µF decoupling capacitor near the receiver module if you see noisy decodes.

## IR LED wiring (two options)

### Option A: simple (short range)

Drive IR LED directly from D3 through a resistor.

- D3 -> series resistor (\(100–220\Omega\)) -> IR LED anode (+)
- IR LED cathode (-) -> GND

This is easy, but the range is limited and depends on your board’s pin current limits.

### Option B: recommended (good range)

Use an NPN transistor as a low-side switch:

- D3 -> base resistor (~\(1k\)) -> NPN base
- NPN emitter -> GND
- NPN collector -> IR LED cathode (-)
- IR LED anode (+) -> 5V through series resistor (start \(47–100\Omega\), tune as needed)

Tune the IR LED series resistor for your LED/transistor so you don’t exceed safe current.

## Quick test checklist

- Upload `device/testing_terminal/testing_terminal.ino`
- Open Serial Monitor at **115200 baud**
- **Receive test**: point any existing LaserTag emitter (gun/vest/dispenser/respawn) at the receiver and confirm:
  - `LED_BUILTIN` blinks
  - Serial prints `IR rx: addr=... cmd=... raw=...`
- **Transmit test**: press each button and confirm Serial prints:
  - Shot: `addr=5 cmd=0`
  - Health: `addr=2 cmd=<HEALTH_DISPENSER_ID>`
  - Ammo: `addr=3 cmd=<AMMO_DISPENSER_ID>`
- If range is weak, switch to the transistor drive option and re-test.

