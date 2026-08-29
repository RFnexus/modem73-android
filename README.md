<p align="center">
 <img width="500" height="140" alt="m73_logo" src="https://github.com/user-attachments/assets/3d88058f-d8bd-4909-b09f-df567c8beb80" />

</p>

modem73-android is a full standalone Android port of [MODEM73](https://github.com/RFnexus/modem73).


Turn any Android phone into a high performance OFDM software modem that works with any HF/VHF/UHF radio. Speeds up to 130 bits to 17 kilobits per second all in the same 2400 Hz channel. 

No flat audio mod or external hardware needed besides an OTG adapter and  cable for your radio. 

<img width="1920" height="1130" alt="573_1x_shots_so" src="https://github.com/user-attachments/assets/7ddc829d-8b5c-4d8d-b2e9-3343d62c1635" />
<p align="center">
<img width="507.5" height="361" alt="preview1" src="https://github.com/user-attachments/assets/962f7592-6469-414f-9c64-3532c5cfe46a" />
</p>









## Features

- All MODEM73 modes: OFDM up to QAM4096, ROBUST HF modes, and MFSK
- Live waterfall, signal meters, and channel activity on the STATUS screen
- CSMA channel access with THRESHOLD, SYNC, and RANKED modes supporting up to 8 users in a shared busy channel
- KISS TCP and control port, so any KISS application can use the phone as a TNC
- LAN mode to share the modem with other machines on your network
- PTT over VOX, USB serial, or Hamlib CAT
- Full RIG control screen for Hamlib radios: frequency, mode, RF power, tuner, meters, and presets when  Hamlib is the selected PTT


## Getting started

1. Install the app and open it
2. Allow microphone access and background use when asked
3. Plug in your OTG adapter in, then your sound device or radio
4. Pick your callsign, mode, and PTT type under CONFIG
5. Restart and get on the air

Tap GUIDE in the header for a rundown of every setting and how to pick a mode.

The modem listens on KISS port 8001 and control port 8073. Turn on LAN access under CONFIG to reach them from other machines.

## Building from source

You need the Android SDK, NDK, and a checkout of the MODEM73 core.

```bash
git clone https://github.com/RFnexus/modem73-android
cd modem73-android
cp local.properties.example local.properties
# set modem73.core.dir in local.properties to your MODEM73 checkout
./gradlew assembleDebug
```



## Third party Notices
Hamlib is licensed under the [LGPL v2.1 license ](https://raw.githubusercontent.com/Hamlib/Hamlib/refs/heads/master/LICENSE)

## LLM disclosure notice
LLMs were used in the development of this project to scaffold the Kotlin UI with Jetpack Compose, Gradle build, and to assist in the debugging process. 
