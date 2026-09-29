package co.lunt.andash

import android.app.Application
import android.content.Context
import co.lunt.andash.data.CalendarSource
import co.lunt.andash.data.DeviceCalendarSource
import co.lunt.andash.data.SystemTimeSource
import co.lunt.andash.settings.SettingsRepository

/** Hand-wired dependencies; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val time = SystemTimeSource()
    val source: CalendarSource = DeviceCalendarSource(context) { time.zone() }
}

class AndashApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
