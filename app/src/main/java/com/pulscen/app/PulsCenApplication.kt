package com.pulscen.app

import android.app.Application
import com.pulscen.app.data.AccountStore
import com.pulscen.app.data.AppRepository
import com.pulscen.app.data.AuthManager
import com.pulscen.app.data.PriceRefresher
import com.pulscen.app.data.SyncManager
import com.pulscen.app.data.SyncPrefs
import com.pulscen.app.work.WorkScheduler

class AppContainer(context: Application) {
    val accountStore = AccountStore(context)
    val syncPrefs = SyncPrefs(context)
    val repository = AppRepository(accountStore)
    val syncManager = SyncManager(repository, accountStore, syncPrefs)
    val authManager = AuthManager(repository, accountStore, syncManager, syncPrefs)
    val priceRefresher = PriceRefresher(repository)
}

class PulsCenApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WorkScheduler.schedule(this)
    }
}
