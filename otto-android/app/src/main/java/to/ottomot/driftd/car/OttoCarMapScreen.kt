package to.ottomot.driftd.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mapbox.maps.extension.androidauto.MapboxCarMap
import com.mapbox.maps.extension.androidauto.mapboxMapInstaller
import to.ottomot.driftd.OttoShellUiState
import to.ottomot.driftd.OttoShellViewModel
import to.ottomot.driftd.R

class OttoCarMapScreen(
    carContext: CarContext,
    private val mapboxCarMap: MapboxCarMap,
    private val viewModel: OttoShellViewModel,
) : Screen(carContext) {
    private val mapObserver = OttoCarMapObserver(carContext.applicationContext, viewModel.state)
    private var latestState: OttoShellUiState = viewModel.state.value

    init {
        mapboxMapInstaller(mapboxCarMap)
            .onCreated(mapObserver)
            .install()
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    mapObserver.start(owner, this@OttoCarMapScreen::invalidate)
                }

                override fun onStop(owner: LifecycleOwner) {
                    mapObserver.stop()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        latestState = viewModel.state.value
        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .build(),
            )
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(recenterAction())
                    .addAction(reportHazardAction())
                    .apply {
                        if (latestState.hasActiveDriveSession) {
                            addAction(endDriveAction())
                        }
                    }
                    .build(),
            )
            .build()
    }

    private fun recenterAction(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_android_auto_recenter_yellow),
                ).setTint(AndroidAutoControlAccent).build(),
            )
            .setOnClickListener {
                if (viewModel.state.value.deviceLocationFix == null) {
                    CarToast
                        .makeText(
                            carContext,
                            carContext.getString(R.string.android_auto_status_location_unavailable),
                            CarToast.LENGTH_SHORT,
                        ).show()
                } else {
                    mapObserver.recenterOnUser()
                }
            }
            .build()

    private fun reportHazardAction(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_android_auto_hazard_yellow),
                ).setTint(AndroidAutoControlAccent).build(),
            )
            .setOnClickListener {
                if (viewModel.state.value.deviceLocationFix == null) {
                    CarToast
                        .makeText(
                            carContext,
                            carContext.getString(R.string.android_auto_status_location_unavailable),
                            CarToast.LENGTH_SHORT,
                        ).show()
                } else {
                    push(OttoCarHazardPickerScreen(carContext, viewModel))
                }
            }
            .build()

    private fun endDriveAction(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_close_clear_cancel),
                ).setTint(CarColor.RED).build(),
            )
            .setOnClickListener {
                push(OttoCarEndDriveScreen(carContext, viewModel))
            }
            .build()

    private fun push(screen: Screen) {
        carContext.getCarService(ScreenManager::class.java).push(screen)
    }

    private companion object {
        val AndroidAutoControlAccent: CarColor =
            CarColor.createCustom(0xFFFFC71F.toInt(), 0xFFFFC71F.toInt())
    }
}

private class OttoCarHazardPickerScreen(
    carContext: CarContext,
    private val viewModel: OttoShellViewModel,
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list =
            androidx.car.app.model.ItemList.Builder()
                .addHazardRow("police", R.string.map_hazard_type_police)
                .addHazardRow("traffic", R.string.map_hazard_type_traffic)
                .addHazardRow("crash", R.string.map_hazard_type_crash)
                .addHazardRow("hazard", R.string.map_hazard_type_hazard)
                .build()
        return ListTemplate.Builder()
            .setSingleList(list)
            .setTitle(carContext.getString(R.string.map_hazard_report_sheet_title))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun androidx.car.app.model.ItemList.Builder.addHazardRow(
        type: String,
        labelRes: Int,
    ): androidx.car.app.model.ItemList.Builder =
        addItem(
            Row.Builder()
                .setTitle(carContext.getString(labelRes))
                .setOnClickListener {
                    val fix = viewModel.state.value.deviceLocationFix
                    if (fix == null) {
                        carContext.getCarService(ScreenManager::class.java).push(
                            OttoCarMessageScreen(
                                carContext = carContext,
                                title = carContext.getString(R.string.android_auto_status_location_unavailable),
                                body = carContext.getString(R.string.android_auto_location_unavailable_body),
                            ),
                        )
                    } else {
                        viewModel.reportMapHazard(type, fix.latitude, fix.longitude)
                        carContext.getCarService(ScreenManager::class.java).pop()
                    }
                }
                .build(),
        )
}

private class OttoCarEndDriveScreen(
    carContext: CarContext,
    private val viewModel: OttoShellViewModel,
) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.android_auto_end_drive_body))
            .setTitle(carContext.getString(R.string.android_auto_end_drive_title))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.android_auto_end_drive))
                    .setBackgroundColor(CarColor.RED)
                    .setOnClickListener {
                        viewModel.requestStopDriveSessionFromAndroidAuto()
                        carContext.getCarService(ScreenManager::class.java).pop()
                    }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.android_auto_cancel))
                    .setOnClickListener {
                        carContext.getCarService(ScreenManager::class.java).pop()
                    }
                    .build(),
            )
            .build()
}

private class OttoCarMessageScreen(
    carContext: CarContext,
    private val title: String,
    private val body: String,
) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(body)
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .build()
}
