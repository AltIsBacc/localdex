# LocalDex

Run a desktop on a **virtual display inside the app**. The display is rendered straight into a SurfaceView: no dock, no cable, no computer, and no video encoding or decoding.

## Requirements

- Samsung device on **One UI 8 or later**
- **Root** (Magisk or KernelSU)

## Usage

1. Install the APK and open LocalDex.
2. Grant superuser access.
3. Optionally enter a display spec (`WIDTHxHEIGHT/DPI`, e.g. `1920x1080/240`). Leave it blank to use the size and density of the screen.
4. Tap **Open virtual display**.

Leaving the app closes the display and its windows.

A display spec with a different aspect ratio than the screen will stretch the picture. Match the screen's ratio or leave it blank.

The view is output only for now: touch and keyboard input are not forwarded to the virtual display.

See the [technical breakdown](docs/TECHNICAL.md) for details.

## Building

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
