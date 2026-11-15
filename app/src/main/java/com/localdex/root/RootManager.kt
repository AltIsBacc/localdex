package com.localdex.root

import android.os.Build
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import com.localdex.App
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.time.Duration.Companion.seconds

object RootManager : RootSession() {
    override val context get() = App.app
    override val timeout get() = 5.seconds

    override suspend fun initServer(server: RootServer) {
        if (Build.VERSION.SDK_INT >= 31) runCatching { HiddenApiBypass.setHiddenApiExemptions("") }
        super.initServer(server)
    }
}
