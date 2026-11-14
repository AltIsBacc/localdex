# LocalDex

Run the DeX desktop **directly on your phone's main display**. No dock, no cable, no computer, and no video encoding or decoding: display 0 itself becomes the desktop.

![The DeX desktop running on-device with several app windows open](docs/images/dex-on-device.jpg)

## Requirements

- Samsung device on **One UI 8 or later**
- **Root** (Magisk or KernelSU)
- Optional: **LSPosed** for the hook layer (see below)

## Usage

1. Install the APK and open LocalDex.
2. Grant superuser access and allow notifications. The notification is how you switch back.
3. Optionally enter a display spec (`WIDTHxHEIGHT/DPI`, e.g. `1920x1080/240`). Leave it blank to keep the native resolution and density.
4. Tap **Enable DeX on main display**.

To go back, tap **Switch back** in the LocalDex notification or in the app. This restores the windowing mode, display size, density and the freeform setting to what they were before.

The windowing mode and display size persist across reboots, so the notification is restored on boot while DeX is active.

A display spec with a different aspect ratio than the screen will stretch the picture. Match the phone's ratio or leave it blank.

## LSPosed (optional)

Root alone forces freeform windowing on display 0. If One UI still refuses to run the desktop shell there, enable the LocalDex module in LSPosed with scope **System Framework** and **System UI**, then reboot once.

The hooks only act while the `debug.localdex.active` system property is `1`. LocalDex sets it when DeX is enabled and clears it when you switch back. System properties under `debug.` do not survive a reboot, so the hooks are inert after every reboot until LocalDex re-asserts them.

Hook targets are AOSP class and method names and are not verified against every One UI build. The module logs which ones it attached to in the LSPosed log.

See the [technical breakdown](docs/TECHNICAL.md) for details.

## Building

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The LSPosed API is fetched from `https://api.xposed.info/`.
