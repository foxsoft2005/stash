package io.github.foxsoft2005.stash

import android.app.Application
import android.content.Context
import io.github.foxsoft2005.stash.ai.GeminiNanoTagger
import io.github.foxsoft2005.stash.ai.MlKitLabelTagger
import io.github.foxsoft2005.stash.ai.NanoConsentStore
import io.github.foxsoft2005.stash.ai.SmartTagger
import io.github.foxsoft2005.stash.data.ItemRepository
import io.github.foxsoft2005.stash.data.PhotoStore
import io.github.foxsoft2005.stash.data.StashDatabase
import io.github.foxsoft2005.stash.data.backup.BackupManager
import io.github.foxsoft2005.stash.data.backup.BackupStatusStore
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
