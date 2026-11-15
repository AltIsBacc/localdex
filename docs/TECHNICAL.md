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

- The flow emits `DisplayReady(displayId)` once and then waits. Cancelling the collection releases the display, and `DisplayActivity` cancels it when the Surface is destroyed, so the display never outlives the view.
- The Surface is fixed to the display size (`setFixedSize`) because the virtual display renders into buffers of exactly that size. A display spec (`WIDTHxHEIGHT/DPI`) is used if set, otherwise the SurfaceView size and the screen density.
- Flags are the set scrcpy uses for `--new-display`: public, presentation, own content only, touch support, rotate with content, own display group, always unlocked, touch feedback disabled and system decorations, plus own focus and device display group on API 33+.
- After the display exists, `enable_freeform_support` is set to 1 (not restored afterwards) and the new display is switched to freeform with `wm set-display-windowing-mode -d <id> 5`, since virtual displays default to fullscreen on recent One UI.
- Backgrounding the activity destroys the Surface and therefore the display and its windows.
- The view is output only: touch and key events are not forwarded to the virtual display yet.
