package com.localdex

import android.app.Application

class App : Application() {

    companion object {
        lateinit var app: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }
}
