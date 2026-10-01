# andash

A full-screen meeting dashboard for an old Android phone (built for a Moto G Play 2024, Android 14).
Big clock on top, your next meeting in the middle, the ones after it in smaller text, and a
countdown to the next meeting at the bottom. See `PLAN.md` and `IMPLEMENTATION_PLAN.md` for the design.

It reads the calendars Android has already synced onto the phone, so every Google account you add
to the phone (6 or more) shows up with no OAuth, API keys or server. The same meeting on several
accounts is shown once. Declined and cancelled events are hidden.

## Toolchain (Mac)

No Android Studio needed. What was installed with Homebrew:

```sh
brew install openjdk@17 gradle android-commandlinetools
brew install --cask android-platform-tools
source env.sh                       # sets JAVA_HOME, ANDROID_HOME and PATH for this shell
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
```

`local.properties` points Gradle at the SDK (`sdk.dir=/opt/homebrew/share/android-commandlinetools`).

## Everyday commands

```sh
source env.sh
./gradlew installDebug          # build + install on the phone (USB or wireless adb)
./gradlew assembleRelease       # signed, minified APK in app/build/outputs/apk/release/
adb install -r -g app/build/outputs/apk/release/app-release.apk   # update in place, keeps settings
adb shell am start -n co.lunt.andash/.MainActivity
adb logcat --pid=$(adb shell pidof -s co.lunt.andash)
adb exec-out screencap -p > shot.png
```

Debug and release builds share one signing key, so you can switch between them without uninstalling.
The key lives in `keystore/andash-release.jks` (passwords in `keystore.properties`). Both are
git-ignored. **Back them up**: a different key means uninstalling the app and losing its settings.

## Tests

```sh
./gradlew testDebugUnitTest     # 40 JVM tests: scheduling, countdown, de-dupe, links, formats, night window
scripts/test-device.sh          # 33 on-device tests on the attached phone
scripts/test-device.sh DashboardUiTest    # one class
```

The on-device tests create throwaway `LOCAL` calendars named `andash-test-*` (six "accounts" at
once) and delete them afterwards; they never touch real calendars, and the app's calendar
choices are reset at the end. `ScreenshotTest` saves PNGs to
`/sdcard/Android/data/co.lunt.andash/files/screenshots` on the phone (delete them after pulling).
Debug builds request `WRITE_CALENDAR` (the tests run inside the app's process); release builds do not.

## Using it

1. On the phone: add your Google accounts, turn on Calendar sync for each, open Google Calendar once
   and check every calendar you want is ticked.
2. Open andash and allow calendar access.
3. **Long-press the clock** for Settings: choose calendars, look-ahead, how long a started meeting
   stays on the big card, the "soon" warning, night mode, force a sync.
4. Make it the phone's appliance (all optional, all reversible):
   - Settings → Apps → Default apps → **Home app** → andash. It then comes back after reboots and
     when Home is pressed.
   - Settings → Apps → andash → Battery → **Unrestricted**.
   - Optional: turn on App pinning and pin andash.
5. **Way out** while andash is the Home app: long-press the clock → Settings → *Open default
   launcher*. Or from the Mac:
   `adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.motorola.launcher3`

## Layout of the code

```
app/src/main/java/co/lunt/andash/
  domain/     pure Kotlin: Schedule (which meeting is "next", countdown state), Dedupe, MeetingLinks, Countdown, NightWindow
  data/       CalendarSource interface; DeviceCalendarSource (CalendarContract), FakeCalendarSource, TimeSource
  settings/   AppSettings + DataStore repository
  ui/         DashboardScreen (pure), Sections, SettingsScreen, DashboardRoot (glue), TimeFormats, Theme
  DashboardViewModel.kt   loads meetings; refreshes on calendar change, resume, time change, midnight, every 5 min
```

New features go in as new sections: `ReservedSlot` in `DashboardScreen` is the hook.
A different data source (an ICS feed, or the Google Calendar API for a work account that can't sync
to the phone) only needs to implement `CalendarSource`.

## Deviations from the plan

- Built with command-line tools instead of Android Studio (the Mac had 11 GB free).
- `compileSdk` is 37 because current Compose requires it; `targetSdk` is 36; `minSdk` is 34.
- Tests ran on the real phone rather than an emulator.
- Sound alerts, weather and the other "future features" are not built yet.

## License

[MIT](LICENSE)
