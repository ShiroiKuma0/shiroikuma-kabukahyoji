package com.github.premnirmal.ticker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.github.premnirmal.ticker.analytics.Analytics
import com.github.premnirmal.ticker.components.Injector
import com.github.premnirmal.ticker.components.LoggingTree
import com.github.premnirmal.ticker.notifications.NotificationsHandler
import com.github.premnirmal.ticker.widget.WidgetDataProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by premnirmal on 2/26/16.
 */
@HiltAndroidApp
open class StocksApp : Application() {

    @Inject lateinit var analytics: Analytics

    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var notificationsHandler: NotificationsHandler

    @Inject lateinit var widgetDataProvider: WidgetDataProvider

    override fun onCreate() {
        initLogger()
        Injector.init(this)
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(appPreferences.nightMode)
        // Re-apply the saved app language so it survives app updates (the system/AppCompat store can
        // reset to the system locale on update); our pref is the source of truth.
        val languageTag = appPreferences.uiLanguageTag
        if (languageTag.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
        initNotificationHandler()
        runBlocking { widgetDataProvider.refreshWidgetDataList() }
    }

    protected open fun initNotificationHandler() {
        notificationsHandler.initialize()
    }

    protected open fun initLogger() {
        Timber.plant(LoggingTree())
    }
}
