package com.localdex

import android.app.Application
import be.mygod.librootkotlinx.RootSession
import kotlin.time.Duration.Companion.seconds

class App : Application() {

    companion object {
        lateinit var root: RootSession
            private set
    }

    override fun onCreate() {
        super.onCreate()
        root = object : RootSession() {
            override val context get() = this@App
            override val timeout get() = 5.seconds
        }
    }
}
