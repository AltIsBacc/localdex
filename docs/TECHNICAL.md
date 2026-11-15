# LocalDex Technical Breakdown

## Overview

LocalDex creates a virtual display in a root process and points its output at a Surface owned by the app. SurfaceFlinger composites the display straight into the app's buffer queue, so nothing is captured, encoded or decoded.

```
DisplayActivity (SurfaceView) ──Surface──▶ librootkotlinx ──▶ root process
                                                               │ VirtualDisplayFlow
                                                               ▼
                                   DisplayManager.createVirtualDisplay(..., surface, flags)
                                                               │
                                   SurfaceFlinger composites into the app's Surface
```

## Root layer

`RootManager` is the single librootkotlinx `RootSession`. Commands and flows go through it:

```kotlin
RootManager.use { server -> server.flow(VirtualDisplayFlow(...)).collect { ... } }
```

The root process stays alive while a `use` block is running, and for a few seconds after the last one.

`Root.exec` is a small `su -c <command>` helper with a timeout. It is used for the root availability check and for the two `settings`/`wm` commands that run after the display exists.

## Virtual display

`DisplayActivity` passes the SurfaceView's `Surface` to `VirtualDisplayFlow`, a `RootFlow` whose constructor arguments are parcelled to the root process (a `Surface` parcels its buffer-producer binder). The root process creates the virtual display with that Surface as its output.

- The flow emits `DisplayReady(displayId, trusted, control)` once and then waits. Cancelling the collection releases the display, and `DisplayActivity` cancels it when the Surface is destroyed, so the display never outlives the view.
- The Surface is fixed to the display size (`setFixedSize`) because the virtual display renders into buffers of exactly that size. A display spec (`WIDTHxHEIGHT/DPI`, optionally `@HZ`) is used if set, otherwise the screen size and an automatic density (see Display settings).
- **Refresh rate.** The `DisplayManager.createVirtualDisplay(name, w, h, dpi, surface, flags)` overload has no refresh rate parameter, so the display runs at the system default (60 Hz here). When a rate is requested on API 34+, the display is created from a `VirtualDisplayConfig` instead, with `setRequestedRefreshRate` (looked up by reflection, in case a release lacks it). The system rounds the request up to a divisor of the physical screen's refresh rate, so on a 90 Hz panel only 90, 45, 30… are exact. If building the config fails, the display is created with the plain overload at the default rate. `DisplayReady.refreshRate` carries the rate the system actually granted, and the app shows a toast when it differs from the request by more than 1 Hz. The app also calls `Surface.setFrameRate` on the SurfaceView with the requested rate, as a hint so an adaptive refresh rate panel does not settle below it.
- Flags are the set scrcpy uses for `--new-display`: public, presentation, own content only, touch support, rotate with content, destroy content on removal and system decorations; on API 33+ also **trusted**, own display group, always unlocked and touch feedback disabled; on API 34+ also own focus and device display group.
- **The display must be trusted.** `DisplayManagerService` clears `SHOULD_SHOW_SYSTEM_DECORATIONS` (and ignores home support) on any display created without `VIRTUAL_DISPLAY_FLAG_TRUSTED`, so no home or desktop ever starts on it and the output stays black. Root satisfies the `ADD_TRUSTED_DISPLAY` check, and root is exempt from the package-name check, so no shell identity is needed. `DisplayReady.trusted` reports whether the resulting `Display` carries `FLAG_TRUSTED`, and the app warns if it does not.
- After the display exists, `enable_freeform_support` and `force_resizable_activities` are set to 1 (not restored afterwards) and the new display is switched to freeform with `wm set-display-windowing-mode -d <id> 5`, since virtual displays default to fullscreen on recent One UI. `force_resizable_activities` matters because a non-resizable activity can be refused on a secondary display, in which case the system starts it on the main display instead.
- Backgrounding the activity destroys the Surface and therefore the display and its windows.

## Display settings

`DisplaySpec` (width, height, dpi, hz) holds the choice, with null for "automatic". `Prefs` stores it as four values, where 0 means automatic, and still reads the single `WIDTHxHEIGHT/DPI` string that earlier versions saved. `MainActivity` offers a spinner of presets plus a Custom field for each part, and `Presets` holds the lists.

- **Resolution** presets are fixed sizes. Automatic uses the window bounds.
- **DPI** presets are the Android density buckets. When the DPI is automatic the density is `width * 160 / 1600`, which keeps the layout 1600 dp wide whatever the resolution, as a desktop expects (192 dpi at 1920 px, 234 dpi at 2340 px). The phone's own density is never used: at about 420 dpi it made the desktop a phone-sized UI with huge icons and truncated labels.
- **Hz** presets are derived from the screen: its highest supported rate divided by 1 to 4, dropping anything under 24 Hz. This is because the requested rate is rounded up to a divisor of the physical rate.

## Input

`DisplayReady.control` is a `Binder` owned by the root process (`ControlBinder`). The app sends input to it as **oneway** transactions (`DexControl`), so injection never blocks the UI thread, and the root process injects real events with `InputManager.injectInputEvent` targeted at the virtual display's id (`InputEvent.setDisplayId`), the same way scrcpy does.

`InputForwarder` translates what the view receives:

- **Mouse**: hover, move, left/right/middle buttons and the wheel are forwarded as real mouse events (`SOURCE_MOUSE`). Each button change produces `ACTION_DOWN` (first button), `ACTION_BUTTON_PRESS`, `ACTION_BUTTON_RELEASE` and `ACTION_UP` (last button), which is what apps such as Chrome expect. The back and forward buttons become `KEYCODE_BACK` and `KEYCODE_FORWARD`.
- **Touch**: one finger is a mouse click or drag, two fingers scroll. Touch is deliberately not forwarded as touch, because One UI's desktop shell crashes when a window caption is dragged by a touchscreen pointer.
- **Keyboard**: every key event reaching the activity is forwarded with its key code, scan code, meta state and repeat count, except volume, power, sleep, wake and brightness keys, which stay with the phone. Back is forwarded too; use the close button to leave.
- **On-screen keyboard**: the keyboard button shows the IME on a view that reports itself as a text editor with `TYPE_NULL`. Key events pass through; committed text is converted to key events through the virtual keyboard's character map, so characters that map to no key are dropped.

Coordinates are mapped from view space to display space with a plain scale, which is exact because the view is sized to the display's aspect ratio.

## Fullscreen and controls

`DisplayActivity` is edge-to-edge: system bars are hidden with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` and re-hidden whenever the window regains focus, the layout extends into the display cutout, and a blank display spec uses the full window bounds so the display covers the whole screen. Edge-to-edge windows are not resized for the keyboard, so the activity applies the IME inset as bottom padding itself and re-fits the surface.

The ✕ and ⌨ buttons are hidden while a non-virtual alphabetic keyboard and a mouse-capable device are both connected. An `InputManager.InputDeviceListener` re-evaluates this when devices are added, removed or changed, while the activity is started.

## Hidden APIs

librootkotlinx waits for the root process to exit with `Process.openPidFd`, which is a blocked hidden API in the app process. Without a bypass it logs `Failed to nonblocking awaitExit` and falls back to a blocking wait. `RootManager.initServer` calls `HiddenApiBypass.setHiddenApiExemptions("")` from the standalone `org.lsposed.hiddenapibypass` library first (no LSPosed installation involved), as VPNHotspot does. Code running inside the root process is not subject to hidden API restrictions.
