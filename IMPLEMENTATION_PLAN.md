# andash: Implementation Plan

This is the step-by-step build plan for the meeting dashboard described in `PLAN.md`. Every phase ends with the app running on the real phone.

In each phase:

- **👤 You do** marks what you (the owner) do by hand, on the Mac or on the phone.
- **🛠 Build** marks what the developer or coding agent writes.
- **✅ Done when** is the check that the phase is finished.

---

## 0. Key Facts and Decisions

| Item | Decision |
|---|---|
| Device | Moto G Play (XT2413V), Android 14 (API 34), 720×1600 LCD, USB-C |
| Language / UI | Kotlin, Jetpack Compose with Material 3 |
| SDK levels | `minSdk 34` (this phone only). `targetSdk` and `compileSdk` use the latest stable SDK |
| Build | Gradle, from Android Studio's current stable template (Compose BOM, version catalog) |
| Orientation | Portrait by default, since the layout is a vertical stack. Landscape can come later as a setting |
| Distribution | Debug or signed APK installed over `adb`. No Play Store |
| Package name | Any unique reverse-domain ID, e.g. `com.<yourname>.andash`. Pick it once and never change it |
| Calendar source | **Primary:** the phone's own calendar database (`CalendarContract`), filled by Android's Google Calendar sync for every Google account on the phone. **Fallbacks:** see §0.1 |

### 0.1 Multiple Google Calendar Accounts (6 or More)

**Primary path: add every Google account to the phone.** Android syncs each account's calendars into one shared on-device database. The app reads all of them with a single `READ_CALENDAR` permission. This needs no Google Cloud project, no OAuth and no server, and it works for 6, 10 or more accounts. Google's sync pushes changes to the phone, so updates usually show up within seconds to a couple of minutes.

**Known risk: Google Workspace (work) accounts.** Some admins require "advanced mobile management." Adding such an account can force the phone into a work profile, and an app in the personal profile can't read the work profile's calendar. If that happens with an account, fall back in this order:

1. **Share the work calendar to one of your personal accounts** (Google Calendar web → Settings for the calendar → Share with specific people → "See all event details"). The shared calendar then syncs through the personal account. This is the easiest option, but the admin may limit external sharing to free/busy only.
2. **Private ICS feed:** Google Calendar web → calendar settings → "Secret address in iCal format." The app fetches this over Wi-Fi. Admins can disable it.
3. **Google Calendar API with OAuth** per account. This is the most work: a Google Cloud project, a consent screen, and publishing the app to "In production" so refresh tokens don't expire after 7 days. Use it only if 1 and 2 fail.

**Architecture consequence:** the app reads meetings through a `CalendarSource` interface. The on-device source ships first. An ICS or API source can be added later without touching the UI.

---

## 1. Mac Setup

**👤 You do:**

1. **Install Android Studio** (current stable). It includes the Android SDK, the emulator, `adb`, and a bundled JDK, so no separate Java install is needed.
   ```sh
   brew install --cask android-studio
   ```
   Or download it from developer.android.com/studio.
2. **Run the first-launch Setup Wizard** and choose "Standard." It downloads the SDK to `~/Library/Android/sdk`.
3. **In Android Studio → Settings → Languages & Frameworks → Android SDK:**
   - SDK Platforms: check **Android 14 (API 34)** and the latest stable platform.
   - SDK Tools: make sure **Android SDK Platform-Tools**, **Android SDK Build-Tools**, and **Android Emulator** are checked.
4. **Put `adb` on your PATH.** Add this to `~/.zshrc`, then open a new terminal:
   ```sh
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   export PATH="$ANDROID_HOME/platform-tools:$PATH"
   ```
   Check it with `adb version`.
5. **Git** (if `git --version` doesn't already work): `xcode-select --install`.
6. **scrcpy** (strongly recommended). It mirrors and controls the phone screen on your Mac, which is handy once the phone sits on a stand.
   ```sh
   brew install scrcpy
   ```

**✅ Done when:** `adb version`, `git --version` and `scrcpy --version` all run.

---

## 2. Phone Setup

**👤 You do (on the phone):**

1. **Update the phone:** Settings → System → System updates. Install what's there.
2. **Enable Developer Options:** Settings → About phone → tap **Build number** 7 times, then enter your PIN.
3. **Settings → System → Developer options:**
   - Turn on **USB debugging**.
   - Turn on **Stay awake** (screen stays on while charging).
   - Optional: turn on **Wireless debugging** (used in §2.1).
4. **Add every Google account:** Settings → Passwords & accounts (the name varies slightly) → Add account → Google. Repeat for all accounts (6+).
5. **For each account:** tap it → Account sync → make sure **Google Calendar** is on.
6. **Open the Google Calendar app** (install it from the Play Store if needed) → ☰ menu. Check that every calendar you want is listed and checked. Then go to Settings → each account → each calendar → make sure **Sync** is on. Shared and subscribed calendars are often off by default.
7. **Battery:** if Settings → Battery offers optimized charging or a charge limit, turn it on. The phone will be plugged in all the time.
8. **Connect the phone to the Mac** with a USB-C data cable. Accept the "Allow USB debugging?" prompt and check **Always allow from this computer**.

**👤 You do (on the Mac):**
```sh
adb devices          # should list the phone as "device" (not "unauthorized")
scrcpy               # a window mirroring the phone should appear
```

**✅ Done when:** `adb devices` shows the phone, and the stock Google Calendar app shows meetings from **all** accounts.

### 2.1 Optional: Wireless adb

This lets the phone stay on its stand and charger while you deploy. It requires the Mac and phone to be on the same Wi-Fi.

1. On the phone: Developer options → Wireless debugging → **Pair device with pairing code**.
2. On the Mac:
   ```sh
   adb pair <ip>:<pair-port>     # enter the 6-digit code
   adb connect <ip>:<port>       # the port shown on the Wireless debugging screen
   ```
   Android Studio's device menu → "Pair Devices Using Wi-Fi" (QR code) does the same thing.

Wireless debugging turns off when the phone reboots or changes Wi-Fi networks, so you'll need to reconnect after either.

---

## 3. Phase 1: Project Skeleton and Hello World

**👤 You do:**
1. Android Studio → New Project → **Empty Activity** (Compose).
   - Name: `andash`
   - Package: your chosen ID (see §0)
   - Save location: `/Users/kmill/andash` (the folder that already holds these plans)
   - Minimum SDK: **API 34**
   - Build language: Kotlin DSL
2. Pick the phone in the device dropdown and press **Run ▶**.

**🛠 Build:**
- `git init`, then commit the generated project with its `.gitignore`.
- Lock orientation to portrait in the manifest.
- Add a `README.md` with the build and install commands below.

**Terminal workflow** (the alternative to the Run button):
```sh
./gradlew installDebug
adb shell am start -n <package>/.MainActivity
adb logcat --pid=$(adb shell pidof -s <package>)
adb exec-out screencap -p > shot.png       # screenshot of the phone
```

**✅ Done when:** "Hello Android" shows on the phone, and the project is committed to git.

---

## 4. Phase 2: Full-Screen Clock

**🛠 Build:**
- **Keep the screen on** with `FLAG_KEEP_SCREEN_ON` on the activity window.
- **Immersive full screen:** hide the status and navigation bars with `WindowInsetsControllerCompat`, using swipe-to-reveal behavior. Draw edge to edge.
- **Dark theme** always: black background, high-contrast text.
- **Clock section:** very large time with tabular (monospaced) digits so the text doesn't jitter. Follow the system 12/24h setting. Put a smaller date line under it.
- **Tick loop:** a coroutine that updates each second, aligned to the second boundary (`delay(1000 - now % 1000)`).
- **Time source:** inject a `Clock` or `TimeSource` everywhere instead of calling `System.currentTimeMillis()` directly, so tests and previews can fake time.

**👤 You do:** put the phone on its stand. Check that the clock reads well from your usual distance, then tell the agent to make it bigger or smaller.

**✅ Done when:** the clock ticks on a full-screen, always-on display.

---

## 5. Phase 3: Full Layout with Fake Data

**🛠 Build:**
- **Domain model** (`Meeting`): id, title, start, end, location, calendar/account name, calendar color, all-day flag, a "declined" flag, meeting link (nullable), and a dedupe key.
- **`CalendarSource` interface** that returns a `Flow<List<Meeting>>`, plus a **`FakeCalendarSource`** with realistic sample days (back-to-back meetings, a gap, an all-day event, overlapping meetings, the same meeting on two accounts).
- **Dashboard screen:** a vertical stack of independent section composables, with rough height shares:

  | Section | ~Height | Content |
  |---|---|---|
  | Clock | 22% | time and date |
  | Next meeting | 28% | title (up to 2 lines), time range, account color bar, location or "Meet"/"Zoom" tag |
  | Upcoming | flexible | the next 3–6 meetings in smaller text, with day headers if they run past today |
  | Countdown | 14% | large "in 12:34", or a state message (see Phase 5) |
  | Reserved slot | 0–14% | empty placeholder for future features. It collapses when empty |

- **Section registry:** the screen renders a list of sections, so later features add a section instead of rewriting the screen.
- **Compose `@Preview`s** of each section, fed by the fake source, so you can iterate on the design on the Mac.

**👤 You do:** review it on the phone (or with scrcpy) and give feedback on sizes, spacing and colors. Expect a few rounds here; it is the cheapest time to change the look.

**✅ Done when:** you like the layout with fake data.

---

## 6. Phase 4: Real Calendar Data (All Accounts)

**🛠 Build:**
- Declare `READ_CALENDAR` in the manifest and request it at runtime on first launch. If it's denied, show a clear message with a button to open the app's settings.
- **`DeviceCalendarSource`:**
  - Query `CalendarContract.Calendars` for every calendar: id, account name, display name, color, visible, sync flag.
  - Query `CalendarContract.Instances` (not `Events`, so recurring meetings expand correctly) from now minus 1 hour to now plus N days (default 7).
  - Join in these event columns: title, location, description, all-day flag, availability, self-attendee status, and `UID_2445` (the shared meeting ID).
- **Filtering rules** (defaults, each switchable later in Settings):
  - Include only calendars the user selected. By default that's every visible calendar except "Holidays" and "Birthdays."
  - Hide declined events (`SELF_ATTENDEE_STATUS == DECLINED`).
  - Keep all-day events out of the next-meeting card and countdown. They may appear as a small chip row later.
  - Optional: hide events marked "free" (`AVAILABILITY_FREE`).
- **Dedupe across accounts:** the same meeting often shows up on several of your accounts. Merge on `UID_2445` + start time. If `UID_2445` is missing, fall back to title + start + end. Keep one entry and note which accounts it came from.
- **Meeting link extraction:** find a Meet, Zoom or Teams URL in the location or description and show it as a small tag.
- **Swap the fake source for the real one** in the app, and keep the fake one for previews and tests.

**👤 You do:** check the phone against the Google Calendar app. Is anything missing, duplicated, or showing when it shouldn't?

**✅ Done when:** the dashboard matches your real calendar across all accounts, with no duplicates or declined meetings.

---

## 7. Phase 5: Live Countdown, State Rules and Refresh

**🛠 Build:**
- **Pure scheduling logic** (`Schedule.kt`, no Android imports, unit tested). Given `now` and a meeting list, it returns:
  - **Primary meeting:**
    - While a meeting is in progress, it stays primary, labeled **NOW** with an "ends in" countdown, for the first 10 minutes (configurable).
    - After that, the next meeting that hasn't started becomes primary.
    - If meetings overlap, use the earliest start.
  - **Upcoming list:** the meetings after the primary one.
  - **Countdown state:**
    - `in 2h 15m` when more than an hour away
    - `in 12:34` when under an hour away
    - `Starting now` for the first 60 seconds
    - `NOW · ends in 18m` while in progress
    - `Free for the rest of the day` when nothing's left today, with tomorrow's first meeting shown in Upcoming
    - `No upcoming meetings` when the list is empty
  - **Urgency level:** normal, then **soon** at 5 minutes or less (amber), then **now** (red or green accent). This drives colors.
- **Refresh triggers** for re-querying the calendar:
  - a `ContentObserver` on `CalendarContract.CONTENT_URI`, since Android signals every time a sync changes data
  - every 5 minutes as a safety net
  - on `onResume`
  - at midnight
  - on `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED`
- **Manual sync:** a long-press (or button in Settings) calls `ContentResolver.requestSync` for each Google account found in the Calendars table, with the manual and expedited flags. Verify on the device that this works without extra permissions.
- **Unit tests** for `Schedule`: back-to-back meetings, overlaps, midnight rollover, DST change, all-day events, an empty day, and the in-progress grace window.

**👤 You do:** create a test event 3 minutes out on any account. Watch it appear, turn amber, count down, then flip to NOW.

**✅ Done when:** new or changed events show within about a minute of Google Calendar sync, the countdown states behave as above, and the tests pass.

---

## 8. Phase 6: Settings Screen

**🛠 Build:**
- **Opening it:** long-press the clock opens Settings. This is also the escape hatch you'll need in Phase 7.
- **Storage:** save settings in Jetpack DataStore.
- **Settings to offer:**
  - **Calendars:** a checklist grouped by account, showing each calendar's color.
  - **Look-ahead days** and **max upcoming items**.
  - **In-progress grace minutes** and **"soon" threshold**.
  - **Hide free events** and **show all-day chips** toggles.
  - **Night mode:** a schedule (e.g. 22:00–07:00) that dims the screen through the window's `screenBrightness` (no special permission needed) and optionally shows only the clock.
  - **Buttons:** "Force sync now," "Open Android settings," and "Open default launcher" (see Phase 7).
- **Version and diagnostics line:** app version, number of calendars and events loaded, and the time of the last refresh.

**✅ Done when:** every setting takes effect right away and survives an app restart.

---

## 9. Phase 7: Appliance Mode

**🛠 Build:**
- **Launcher registration:** add an intent filter with the `HOME` and `DEFAULT` categories to `MainActivity`. As the default Home app, the dashboard comes back after reboots, crashes and Home presses. Android 14 doesn't let apps open themselves in the background on boot, so this is the reliable route.
- **Crash resilience:** catch errors per section, so one broken section shows an error box instead of crashing the app.
- **Handling Home presses** without stacking duplicate screens: `launchMode="singleTask"`.
- **Escape hatch:** the Settings button "Open default launcher" starts the stock Moto launcher by its package, so you can always get to the rest of the phone.

**👤 You do:**
1. Settings → Apps → Default apps → **Home app** → choose **andash**.
2. Settings → Apps → andash → Battery → **Unrestricted**.
3. Optional: Settings → Security → **App pinning** on, then pin andash. This blocks accidental exits; unpin by holding Back and Overview.
4. Reboot the phone and confirm the dashboard comes back on its own.

**To undo:** set the Home app back to the Moto launcher from the andash Settings screen, or over adb:
```sh
adb shell cmd package set-home-activity com.motorola.launcher3/.Launcher   # confirm the exact name with: adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
```

We skip full "device owner" lock-task mode on purpose. It requires a phone with no accounts on it, which conflicts with syncing your Google accounts.

**✅ Done when:** after a reboot, the phone boots straight into the dashboard and stays there, with a working way out.

---

## 10. Phase 8: Hardening and Release Build

**🛠 Build:**
- **Signed release APK:** create a keystore and keep it **out of git**. Make a `./gradlew assembleRelease` build with R8 turned on.
- **Small updates later:** `adb install -r app-release.apk` updates the app in place and keeps your settings.
- **Burn-in protection:** the screen is LCD, so the risk is low. Still, shift the whole layout by a few pixels every 10 minutes.
- **Power loss or Wi-Fi drop:** show a small "calendar may be stale" dot when the last data change is older than a threshold and the phone has no network.
- **Readability check:** view it at night-mode brightness and at full brightness.

**👤 You do:** let it run for a few full workdays and note anything wrong: a missed meeting, a wrong countdown, freezes, or heat from the phone.

**✅ Done when:** it runs a whole week without anyone touching it.

---

## 11. Iteration Workflow (Ongoing)

1. Change the code on the Mac, with Compose previews for fast visual tweaks.
2. Run `./gradlew installDebug` (over USB or wireless). The dashboard relaunches.
3. Check the result with `scrcpy` or by looking at the phone. Use `adb logcat` for errors.
4. Commit to git after each working change.
5. For time-dependent behavior, add a debug-only "fake now" setting to jump to any time of day instead of waiting.

---

## 12. Future Features (High Level)

Each feature is a new section in the reserved slot, or a setting. Rough order of value:

| Feature | Notes |
|---|---|
| **Pre-meeting alert** | A chime and/or full-screen flash at T-2 min and T-0. Configurable, and muted at night |
| **Join info** | Show the Meet/Zoom code large, or a QR code for the join link to scan with your laptop or main phone |
| **Account color legend** | Show which account or calendar each meeting came from. Useful with 6+ accounts |
| **Today's timeline bar** | A thin horizontal bar of the day's meetings with a "now" marker |
| **"Free until" / focus time** | During gaps, show "Free until 2:30 (1h 45m)" |
| **Weather** | Current conditions and forecast (needs an API key and a network section) |
| **All-day chips** | OOO, travel and holidays as small chips under the date |
| **Busy light** | Turn the whole screen red or green when you're in a meeting, visible to people nearby |
| **Other sources** | ICS feeds or the Google Calendar API (§0.1), plus non-Google calendars such as Outlook ICS |
| **Now playing / Slack status** | Integrations that would need tokens and network access |
| **Remote config** | Change settings from the Mac, via `adb` broadcasts or a small local web page |
| **Landscape layout** | Two columns: clock and countdown on the left, meetings on the right |

---

## 13. Open Questions for You

1. Are any of the 6+ accounts **Google Workspace** accounts with device-management rules? That decides whether the §0.1 fallbacks are needed.
2. **Portrait or landscape** on the stand?
3. While in a meeting, should the big card keep showing the current meeting or jump straight to the next one? The default is a 10-minute grace window.
4. Do you want a **sound** before meetings, or silent only?
