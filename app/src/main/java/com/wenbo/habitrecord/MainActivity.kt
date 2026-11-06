package com.wenbo.habitrecord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.wenbo.habitrecord.di.LocalAppContainer
import com.wenbo.habitrecord.navigation.HabitRecordAppScaffold
import com.wenbo.habitrecord.ui.theme.HabitRecordTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HabitRecordApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                HabitRecordTheme {
                    HabitRecordAppScaffold()
                }
            }
        }
    }
}
