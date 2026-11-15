package com.localdex.root

import android.os.Build
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import com.localdex.App
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.time.Duration.Companion.seconds

/**
 * The single librootkotlinx session. Every root command and flow goes through [use]:
 *
 *     RootManager.use { it.execute(SomeCommand()) }
 *     RootManager.use { emitAll(it.flow(SomeFlow())) }
 *
 * The root process stays alive for as long as a [use] block is running, and for [timeout] after the last one.
 */
object RootManager : RootSession() {
    override val context get() = App.app
    override val timeout get() = 5.seconds

    override suspend fun initServer(server: RootServer) {
        if (Build.VERSION.SDK_INT >= 31) runCatching { HiddenApiBypass.setHiddenApiExemptions("") }
        super.initServer(server)
    }
}
