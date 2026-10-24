package io.github.foxsoft2005.stash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.foxsoft2005.stash.data.Places
import io.github.foxsoft2005.stash.stashContainer
import io.github.foxsoft2005.stash.ui.capture.CaptureScreen
import io.github.foxsoft2005.stash.ui.capture.CaptureViewModel
import io.github.foxsoft2005.stash.ui.edit.EditItemScreen
import io.github.foxsoft2005.stash.ui.edit.EditItemViewModel
import io.github.foxsoft2005.stash.ui.home.BackupViewModel
import io.github.foxsoft2005.stash.ui.home.HomeScreen
import io.github.foxsoft2005.stash.ui.home.HomeViewModel
import io.github.foxsoft2005.stash.ui.places.PlacesScreen
import io.github.foxsoft2005.stash.ui.places.PlacesViewModel
import kotlinx.serialization.Serializable

// Navigation 3: the back stack is just a list of @Serializable keys that we own.
@Serializable data object HomeRoute : NavKey
@Serializable data object CaptureRoute : NavKey
@Serializable data object PlacesRoute : NavKey
@Serializable data class EditRoute(val itemId: Long? = null, val photoPath: String? = null) : NavKey

@Composable
fun StashNavigation(startWithCamera: Boolean) {
    val container = LocalContext.current.stashContainer
    // Single call site, so the saved back stack is restored after rotation / process death;
    // the initial keys only matter on first launch.
    val initial: Array<NavKey> = if (startWithCamera) arrayOf(HomeRoute, CaptureRoute) else arrayOf(HomeRoute)
    val backStack = rememberNavBackStack(*initial)

    // The place being browsed on Home. Lives here (not in Home's ViewModel) because the Places
    // screen sets it, and rememberSaveable keeps it across rotation and process death.
    var placeFilter by rememberSaveable { mutableStateOf<String?>(null) }

    fun popToHome() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // Each screen gets its own ViewModelStore, cleared when popped.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    viewModel = viewModel {
                        HomeViewModel(
                            repository = container.repository,
                            backupStatus = container.backupStatus,
                            sortStore = container.itemSort,
                            appScope = container.appScope,
                            initialPlace = placeFilter,
                        )
                    },
                    backupViewModel = viewModel { BackupViewModel(container.backup) },
                    placeFilter = placeFilter,
                    onClearPlaceFilter = { placeFilter = null },
                    onCapture = { backStack.add(CaptureRoute) },
                    onOpenItem = { id -> backStack.add(EditRoute(itemId = id)) },
                    onOpenPlaces = { backStack.add(PlacesRoute) },
                )
            }
            entry<PlacesRoute> {
                PlacesScreen(
                    viewModel = viewModel { PlacesViewModel(container.repository) },
                    onPick = { place ->
                        placeFilter = place
                        backStack.removeLastOrNull()
                    },
                    onRenamed = { fromKey, newName ->
                        // Browsing the renamed place: keep browsing it under its new name.
                        if (placeFilter?.let(Places::key) == fromKey) placeFilter = newName
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<CaptureRoute> {
                CaptureScreen(
                    viewModel = viewModel { CaptureViewModel(container.photos) },
                    onPhotoTaken = { path ->
                        // Replace the camera with the form, so Back from the form returns Home.
                        backStack.removeLastOrNull()
                        backStack.add(EditRoute(photoPath = path))
                    },
                    onClose = { backStack.removeLastOrNull() },
                )
            }
            entry<EditRoute> { route ->
                EditItemScreen(
                    viewModel = viewModel {
                        EditItemViewModel(
                            repository = container.repository,
                            tagger = container.tagger,
                            photos = container.photos,
                            appScope = container.appScope,
                            itemId = route.itemId,
                            newPhotoPath = route.photoPath,
                        )
                    },
                    onDone = ::popToHome,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
