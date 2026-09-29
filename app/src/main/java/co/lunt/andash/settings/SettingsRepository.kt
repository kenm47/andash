package co.lunt.andash.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "andash_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val lookAheadDays = intPreferencesKey("look_ahead_days")
        val maxUpcoming = intPreferencesKey("max_upcoming")
        val graceMinutes = intPreferencesKey("grace_minutes")
        val soonMinutes = intPreferencesKey("soon_minutes")
        val hideFree = booleanPreferencesKey("hide_free")
        val showAllDayChips = booleanPreferencesKey("show_all_day_chips")
        val nightEnabled = booleanPreferencesKey("night_enabled")
        val nightStart = intPreferencesKey("night_start")
        val nightEnd = intPreferencesKey("night_end")
        val nightClockOnly = booleanPreferencesKey("night_clock_only")
        val on = stringSetPreferencesKey("calendars_on")
        val off = stringSetPreferencesKey("calendars_off")
        val debugOffset = intPreferencesKey("debug_offset_minutes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            lookAheadDays = p[Keys.lookAheadDays] ?: d.lookAheadDays,
            maxUpcoming = p[Keys.maxUpcoming] ?: d.maxUpcoming,
            graceMinutes = p[Keys.graceMinutes] ?: d.graceMinutes,
            soonMinutes = p[Keys.soonMinutes] ?: d.soonMinutes,
            hideFree = p[Keys.hideFree] ?: d.hideFree,
            showAllDayChips = p[Keys.showAllDayChips] ?: d.showAllDayChips,
            nightEnabled = p[Keys.nightEnabled] ?: d.nightEnabled,
            nightStartMinute = p[Keys.nightStart] ?: d.nightStartMinute,
            nightEndMinute = p[Keys.nightEnd] ?: d.nightEndMinute,
            nightClockOnly = p[Keys.nightClockOnly] ?: d.nightClockOnly,
            explicitlyOn = p[Keys.on].toIds(),
            explicitlyOff = p[Keys.off].toIds(),
            debugOffsetMinutes = p[Keys.debugOffset] ?: d.debugOffsetMinutes,
        )
    }

    suspend fun setLookAheadDays(v: Int) = edit { it[Keys.lookAheadDays] = v.coerceIn(1, 30) }
    suspend fun setMaxUpcoming(v: Int) = edit { it[Keys.maxUpcoming] = v.coerceIn(0, 12) }
    suspend fun setGraceMinutes(v: Int) = edit { it[Keys.graceMinutes] = v.coerceIn(0, 60) }
    suspend fun setSoonMinutes(v: Int) = edit { it[Keys.soonMinutes] = v.coerceIn(1, 30) }
    suspend fun setHideFree(v: Boolean) = edit { it[Keys.hideFree] = v }
    suspend fun setShowAllDayChips(v: Boolean) = edit { it[Keys.showAllDayChips] = v }
    suspend fun setNightEnabled(v: Boolean) = edit { it[Keys.nightEnabled] = v }
    suspend fun setNightStart(minute: Int) = edit { it[Keys.nightStart] = minute.mod(24 * 60) }
    suspend fun setNightEnd(minute: Int) = edit { it[Keys.nightEnd] = minute.mod(24 * 60) }
    suspend fun setNightClockOnly(v: Boolean) = edit { it[Keys.nightClockOnly] = v }
    suspend fun setDebugOffsetMinutes(v: Int) = edit { it[Keys.debugOffset] = v }

    suspend fun setCalendarEnabled(id: Long, enabled: Boolean) = edit { p ->
        val on = p[Keys.on].toIds() - id
        val off = p[Keys.off].toIds() - id
        p[Keys.on] = (if (enabled) on + id else on).map(Long::toString).toSet()
        p[Keys.off] = (if (enabled) off else off + id).map(Long::toString).toSet()
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Set<String>?.toIds(): Set<Long> = this.orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
}
