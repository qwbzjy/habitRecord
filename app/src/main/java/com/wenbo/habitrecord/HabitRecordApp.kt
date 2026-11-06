package com.wenbo.habitrecord

import android.app.Application
import com.wenbo.habitrecord.di.AppContainer

class HabitRecordApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
