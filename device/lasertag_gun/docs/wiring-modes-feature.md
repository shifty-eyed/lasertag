## I want to introduce a switching between 2 wiring modes:

### mode 1 - fully wireless
as it is now, both VEST and GUN connected to android phone, the behavior is the same and the code is the same, on hardware side the vest doesn't have fire trigger so it just doesn't happen

### mode 2 - vest and gun wired
introducing new setup: west and gun connected with 4 wires: vcc 5v, ground, and 2 for UART communication
GUN instead of bluetooth should use serial connection
WEST still has bluetooth serial to the android phone but in addition routes the events from the phone to gun and back
using pins 16, 17 for HW serial 2, 
for west: TX pin 17, RX pin 16
for gun: TX pin 16, RX pin 17

this way we reduce the radio frequency isage and may have single battely to power both devices.

### Implementation
**IMPORTANT** try to implement it with onlty necessary new logic, I expect maximum reuse of existing logic just routing wehn west recaive the message from the phone it also route it to the gun and back.
Let's add a `#define` switch between 2 modes, the current behavior should still be supported

Define the wiring mode in `src/definitions.h`:
```
#define WIRING_MODE_WIRELESS 1
#define WIRING_MODE_WIRED 2
#define WIRING_MODE WIRING_MODE_WIRELESS
```

Vest IR behavior stays local: when the vest receives IR it notifies the phone as before and does not affect the gun.



