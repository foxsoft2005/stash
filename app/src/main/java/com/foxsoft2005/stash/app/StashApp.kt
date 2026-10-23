package com.foxsoft2005.stash.app

import android.app.Application
import android.content.Context
import com.foxsoft2005.stash.app.ai.GeminiNanoTagger
import com.foxsoft2005.stash.app.ai.MlKitLabelTagger
import com.foxsoft2005.stash.app.ai.NanoConsentStore
import com.foxsoft2005.stash.app.ai.SmartTagger
import com.foxsoft2005.stash.app.data.ItemRepository
import com.foxsoft2005.stash.app.data.PhotoStore
import com.foxsoft2005.stash.app.data.StashDatabase
import com.foxsoft2005.stash.app.data.backup.BackupManager
import com.foxsoft2005.stash.app.data.backup.BackupStatusStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StashApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual DI: small app, no framework needed. Swap for Hilt/Metro/Koin as it grows. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val photos = PhotoStore(context)
    private val database = StashDatabase.create(context)
    val repository = ItemRepository(database.itemDao(), photos)
    val backupStatus = BackupStatusStore(context)
    val backup = BackupManager(context, database.itemDao(), photos, backupStatus)

    val tagger = SmartTagger(
        nano = GeminiNanoTagger(),
        labels = MlKitLabelTagger(),
        consent = NanoConsentStore(context),
        appScope = appScope,
    ).also { tagger ->
        appScope.launch { tagger.prepare() }
    }
}

val Context.stashContainer: AppContainer
    get() = (applicationContext as StashApp).container
