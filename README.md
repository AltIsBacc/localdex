# LocalDex

Run a desktop on a **virtual display inside the app**. The display is rendered straight into a SurfaceView: no dock, no cable, no computer, and no video encoding or decoding.

## Requirements

- Samsung device on **One UI 8 or later**
- **Root** (Magisk or KernelSU)

## Usage

1. Install the APK and open LocalDex.
2. Grant superuser access.
3. Pick a resolution, density (DPI) and refresh rate, or choose **Custom** in any of them to type your own. **Automatic** uses this screen's size, a desktop-sized density and the system's default refresh rate.
4. Tap **Open virtual display**.

Leaving the app closes the display and its windows.

### Display settings

- **Resolution**: common sizes from 1280×720 to 4K, or `WIDTHxHEIGHT` (200 to 8192 pixels).
- **Density**: the standard Android buckets (120 to 480 dpi), or any value from 72 to 800. Automatic gives a desktop-like layout 1600 dp wide whatever the resolution (1920 px is 192 dpi, 2340 px is 234 dpi), never the phone's own density, which would make a phone-sized UI. Lower values make everything smaller and fit more on screen.
- **Refresh rate**: the system rounds the request up to a divisor of the screen's own rate, so the presets are the screen's rate divided by 1 to 4 (on a 90 Hz panel: 90, 45 and 30). Custom accepts 1 to 240 Hz, but a value that is not a divisor is rounded and the app tells you what the system actually gave.

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
