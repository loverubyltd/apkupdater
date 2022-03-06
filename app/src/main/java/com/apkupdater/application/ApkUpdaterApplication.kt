package com.apkupdater.application

import android.content.Context
import androidx.multidex.MultiDexApplication
import com.apkupdater.di.mainModule
import org.acra.BuildConfig
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ApkUpdaterApplication : MultiDexApplication() {

	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)

//		initAcra {
//			enabled = false
//			buildConfigClass = BuildConfig::class.java
//			reportFormat = StringFormat.JSON
//
//			httpSender {
//				httpMethod = HttpSender.Method.POST
//				uri = "https://collector.tracepot.com/8ead3e03"
//			}
//		}
	}

	override fun onCreate() {
		super.onCreate()
		initKoin()
	}

	private fun initKoin() = startKoin{
        androidLogger()
		androidContext(this@ApkUpdaterApplication)
		modules(mainModule)
	}
}
