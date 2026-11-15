package com.localdex.root

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Parcelable
import android.util.Log
import android.view.Surface
import be.mygod.librootkotlinx.ParcelableBinder
import be.mygod.librootkotlinx.RootFlow
import be.mygod.librootkotlinx.systemContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisplayReady(val displayId: Int, val trusted: Boolean, val control: ParcelableBinder) : Parcelable

/**
 * Runs in the root process. Creates a virtual display whose output is [surface], the Surface of a SurfaceView living
 * in the app, so SurfaceFlinger composites the display straight into the app's buffer queue: no capture, no encoder,
 * no decoder.
 *
 * Emits [DisplayReady] once, then stays alive until the collector is cancelled. Cancelling releases the display,
 * so the display can never outlive the SurfaceView that is showing it.
 */
@Parcelize
class VirtualDisplayFlow(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val dpi: Int,
) : RootFlow<DisplayReady> {
    // Hidden DisplayManager.VIRTUAL_DISPLAY_FLAG_* values, same set scrcpy uses for --new-display.
    private companion object {
        const val PUBLIC = 1 shl 0
        const val PRESENTATION = 1 shl 1
        const val OWN_CONTENT_ONLY = 1 shl 3
        const val SUPPORTS_TOUCH = 1 shl 6
        const val ROTATES_WITH_CONTENT = 1 shl 7
        const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        const val SHOULD_SHOW_SYSTEM_DECORATIONS = 1 shl 9
        const val TRUSTED = 1 shl 10
        const val OWN_DISPLAY_GROUP = 1 shl 11
        const val ALWAYS_UNLOCKED = 1 shl 12
        const val TOUCH_FEEDBACK_DISABLED = 1 shl 13
        const val OWN_FOCUS = 1 shl 14
        const val DEVICE_DISPLAY_GROUP = 1 shl 15

        const val DISPLAY_FLAG_TRUSTED = 1 shl 7
        const val TAG = "LocalDex"
    }

    override fun flow() = flow {
        var flags = PUBLIC or PRESENTATION or OWN_CONTENT_ONLY or SUPPORTS_TOUCH or ROTATES_WITH_CONTENT or
            DESTROY_CONTENT_ON_REMOVAL or SHOULD_SHOW_SYSTEM_DECORATIONS
        if (Build.VERSION.SDK_INT >= 33) {
            flags = flags or TRUSTED or OWN_DISPLAY_GROUP or ALWAYS_UNLOCKED or TOUCH_FEEDBACK_DISABLED
        }
        if (Build.VERSION.SDK_INT >= 34) flags = flags or OWN_FOCUS or DEVICE_DISPLAY_GROUP
        val display = checkNotNull(
            systemContext.getSystemService(DisplayManager::class.java)
                .createVirtualDisplay("LocalDex", width, height, dpi, surface, flags)
        ) { "DisplayManager refused to create the virtual display" }
        try {
            val created = display.display
            Log.i(TAG, "Created $created")
            val control = ControlBinder(InputInjector(created.displayId))
            emit(
                DisplayReady(
                    created.displayId,
                    (created.flags and DISPLAY_FLAG_TRUSTED) != 0,
                    ParcelableBinder(control),
                )
            )
            awaitCancellation()
        } finally {
            display.release()
            surface.release()
        }
    }
}
