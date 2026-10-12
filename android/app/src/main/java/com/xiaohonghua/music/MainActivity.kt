package com.xiaohonghua.music

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val model: MusicModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if(Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener {it.remove()}
        setTheme(R.style.AppTheme)
        setContent { MusicApp(model,showStartup=savedInstanceState == null) }
    }
}
