package com.skt.nugu.app

import android.app.Application

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        /**
         * Init ClientManager
         */
        ClientManager.init(this)
    }
}
