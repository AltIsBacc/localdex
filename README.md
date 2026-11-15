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

The picture is letterboxed to the display's aspect ratio. Keyboard, mouse and touch are forwarded to the virtual display:

- physical keyboard and mouse work directly, including right click, middle click, back/forward buttons and the wheel
- one finger acts as the mouse, two fingers scroll
- the ⌨ button shows the on-screen keyboard
- the ✕ button closes the display (Back is forwarded to the display); both buttons can be moved by long-pressing and dragging
- the view is immersive fullscreen; swipe from a screen edge to reveal the system bars
- when a physical keyboard and a mouse are both connected, the ✕ and ⌨ buttons are hidden; leave with the Home gesture or Recents

See the [technical breakdown](docs/TECHNICAL.md) for details.

## Building

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
