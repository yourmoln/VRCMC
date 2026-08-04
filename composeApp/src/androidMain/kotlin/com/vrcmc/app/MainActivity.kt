package com.vrcmc.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
class MainActivity:ComponentActivity(){override fun onCreate(s:Bundle?){super.onCreate(s);initDeviceStorage(this);setContent{VrcmcApp()}}}
