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

The display and its windows keep running while the app is in the background. Tap the notification to come back to it. It closes only when you press the ✕ button or the **Close** action on the notification.

### Display settings

A summary under the pickers shows what you will get before you open the display: the pixel size and density, the layout size in dp (a desktop wants well over 1000 dp wide) and the refresh rate, including what the system will round a request to. A custom value that is not valid is flagged in its field and the Open button stays disabled until it is fixed. **Reset to automatic** puts all three back.

- **Resolution**: common sizes from 1280×720 to 4K, or `WIDTHxHEIGHT` (200 to 8192 pixels).
- **Density**: the Android buckets (120 to 480 dpi) with extra steps from 120 to 240, or any value from 72 to 800. Automatic gives a desktop-like layout 1600 dp wide whatever the resolution (1920 px is 192 dpi, 2340 px is 234 dpi), never the phone's own density, which would make a phone-sized UI. Lower values make everything smaller and fit more on screen.
- **Refresh rate**: the system rounds the request up to a divisor of the screen's own rate, so the presets are the screen's rate divided by 1 to 4 (on a 90 Hz panel: 90, 45 and 30). Custom accepts 1 to 240 Hz, but a value that is not a divisor is rounded and the app tells you what the system actually gave.

The picture is letterboxed to the display's aspect ratio. Keyboard, mouse and touch are forwarded to the virtual display:

- physical keyboard and mouse work directly, including right click, middle click, back/forward buttons and the wheel
- one finger acts as the mouse, two fingers scroll; the input button cycles **Auto → Touch → Mouse**. Touch sends fingers as real multi-touch, for games and apps that ignore the mouse. Auto picks per app: touch for games and for apps on your touch list, mouse for everything else. Switching to Touch adds the focused app to the touch list, switching to Mouse removes it.
- the ⌨ button shows the on-screen keyboard
- the ✕ button closes the display (Back is forwarded to the display); both buttons can be moved by long-pressing and dragging
- leaving the app does not close the display; reopen LocalDex and tap **Return to virtual display**, or tap the notification
- the view is immersive fullscreen; swipe from a screen edge to reveal the system bars
- when a physical keyboard and a mouse are both connected, the ✕ and ⌨ buttons are hidden; leave with the Home gesture or Recents and close the display from the notification

See the [technical breakdown](docs/TECHNICAL.md) for details.

## Building

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
