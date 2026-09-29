package co.lunt.andash

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.lunt.andash.data.LoadOptions
import co.lunt.andash.model.CalendarInfo
import co.lunt.andash.model.Meeting
import co.lunt.andash.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Everything loaded from the calendar, independent of "now". */
data class DataState(
    val meetings: List<Meeting> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
    val loadedAt: Instant? = null,
    val permissionGranted: Boolean = false,
    val error: String? = null,
    /** False until the first load finishes, so the UI doesn't flash the permission prompt. */
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as AndashApp).container
    val time = container.time
    val settingsRepo = container.settings

    private val kicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val settings: StateFlow<AppSettings> = container.settings.settings
        .onEach { time.offset = Duration.ofMinutes(it.debugOffsetMinutes.toLong()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _data = MutableStateFlow(DataState())
    val data: StateFlow<DataState> = _data

    init {
        settings
            .flatMapLatest { s ->
                merge(flowOf(Unit), container.source.changes(), kicks, every(REFRESH_EVERY), everyMidnight())
                    .conflate()
                    .map { s }
            }
            .onEach { s -> _data.value = load(s) }
            .launchIn(viewModelScope)
    }

    /** Re-read the calendar now (resume, time change, permission granted). */
    fun refresh() {
        kicks.tryEmit(Unit)
    }

    fun forceSync(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val asked = container.source.requestSync()
            onResult(asked)
        }
    }

    private suspend fun load(s: AppSettings): DataState {
        val granted = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val now = time.now()
        if (!granted) return DataState(permissionGranted = false, loaded = true, loadedAt = now)
        return try {
            val zone = time.zone()
            val from = now.atZone(zone).toLocalDate().minusDays(1).atStartOfDay(zone).toInstant()
            val to = now.plus(Duration.ofDays(s.lookAheadDays.toLong()))
            val calendars = container.source.calendars()
            val meetings = container.source.load(from, to, LoadOptions(isEnabled = s::isEnabled, hideFree = s.hideFree))
            DataState(meetings, calendars, now, permissionGranted = true, loaded = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the last good meetings if a refresh fails.
            _data.value.copy(error = e.message ?: e.javaClass.simpleName, permissionGranted = true, loaded = true)
        }
    }

    private fun every(period: Duration): Flow<Unit> = flow {
        while (true) {
            delay(period.toMillis())
            emit(Unit)
        }
    }

    private fun everyMidnight(): Flow<Unit> = flow {
        while (true) {
            val now = time.now()
            val zone: ZoneId = time.zone()
            val nextMidnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
            emit(Unit)
        }
    }

    private companion object {
        val REFRESH_EVERY: Duration = Duration.ofMinutes(5)
    }
}
