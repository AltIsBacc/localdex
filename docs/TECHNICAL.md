# LocalDex Technical Breakdown

## Overview

LocalDex runs root commands that put the default display (id 0) into freeform windowing. The system draws the desktop itself, so nothing is captured, encoded, decoded or injected.

```
MainActivity ──▶ DexService (foreground, notification with "Switch back")
                     │
                     ▼
                 DexMode ──su──▶ settings / wm / setprop
                                      │
                                      ▼
                         display 0 runs in freeform
```

## Root layer

`Root.exec` runs `su -c <command>` with a timeout and returns the exit code and output. `DexMode.enable` does the following in order:

1. Snapshot the current state: the `enable_freeform_support` setting, and the size and density overrides reported by `wm size` and `wm density`. The snapshot is persisted so a restore survives process death.
2. `settings put global enable_freeform_support 1`
3. `setprop debug.localdex.active 1`
4. If a display spec was given: `wm size WxH` and `wm density D`
5. `wm set-display-windowing-mode -d 0 5` (5 is `WINDOWING_MODE_FREEFORM`)

If any step fails, `disable` runs immediately.

`DexMode.disable` sets the windowing mode back to 0 (undefined, which restores the default heuristics) and falls back to 1 (fullscreen) if that is rejected. It then restores size and density from the snapshot, restores or deletes the freeform setting, and clears the property. The active flag is only cleared if every step succeeded, so a failed switch-back can be retried from the notification.

## Service and notification

`DexService` is a foreground service. Its notification carries the **Switch back** action, a `PendingIntent.getService` that works without the activity. Operations are serialized with a mutex. The service is sticky: if the system restarts it while DeX is active it re-posts the notification and re-asserts the property.

`BootReceiver` starts the service on boot when DeX is active, because the windowing mode and display size persist across reboots.

## In-app virtual display

A second mode shows a separate virtual display inside the app instead of touching display 0.

```
DisplayActivity (SurfaceView) ──Surface──▶ librootkotlinx ──▶ root process
                                                               │ VirtualDisplayFlow
                                                               ▼
                                   DisplayManager.createVirtualDisplay(..., surface, flags)
                                                               │
                                   SurfaceFlinger composites into the app's Surface
```

`RootManager` is the single `RootSession`. `DisplayActivity` passes the SurfaceView's `Surface` to `VirtualDisplayFlow`, a `RootFlow` whose constructor arguments are parcelled to the root process (a `Surface` parcels its buffer-producer binder). The root process creates the virtual display with that Surface as its output, so frames go from SurfaceFlinger into the app's buffer queue with no capture, encoder or decoder in between.

- The flow emits `DisplayReady(displayId)` once and then waits. Cancelling the collection releases the display, and `DisplayActivity` cancels it when the Surface is destroyed, so the display never outlives the view.
- The Surface is fixed to the display size (`setFixedSize`) because the virtual display renders into buffers of exactly that size. A display spec from the main screen (`WIDTHxHEIGHT/DPI`) is used if set, otherwise the SurfaceView size and the screen density.
- Flags are the set scrcpy uses for `--new-display`: public, presentation, own content only, touch support, rotate with content, own display group, always unlocked, touch feedback disabled and system decorations, plus own focus and device display group on API 33+.
- After the display exists, `enable_freeform_support` is set to 1 (not restored afterwards) and the new display is switched to freeform with `wm set-display-windowing-mode -d <id> 5`, since virtual displays default to fullscreen on recent One UI.
- Backgrounding the activity destroys the Surface and therefore the display and its windows.
- The view is output only: touch and key events are not forwarded to the virtual display yet.

## LSPosed layer (optional)

`DexHook` is a legacy Xposed API module (`assets/xposed_init`) scoped to `android` and `com.android.systemui`.

- **system_server**: `DisplayWindowSettings.getWindowingModeLocked` returns freeform for display 0. All overloads are hooked and the `DisplayContent` argument is located by class name.
- **system_server and System UI**: `canEnterDesktopMode`, `isDeviceEligibleForDesktopMode` and `canInternalDisplayHostDesktops` on the AOSP `DesktopModeHelper` / `DesktopModeStatus` classes return `true`. Only boolean-returning overloads are touched.

Every hook checks `debug.localdex.active` on each call. Missing classes are logged and skipped, and nothing throws into the hooked process. The class and method names come from AOSP and may differ on a given One UI build.

## Why a `debug.` property

The module cannot read the app's private storage from `system_server`, and a persistent flag could keep the hooks active across a bad boot. A `debug.*` property is readable from both processes, set by root, and cleared by a reboot.
