package to.ottomot.driftd

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val OttoPreferencesName = "otto_prefs"

internal val Context.ottoPrefs: DataStore<Preferences> by preferencesDataStore(OttoPreferencesName)
