# andash: Meeting Dashboard for an Old Android Phone

High-level plan. A later pass will turn each step into a detailed spec.

**Target device:** Moto G Play (XT2413V), Android 14 (API 34), USB-C.

## Goal

The old Motorola phone runs one full-screen app on a charger and shows:

1. **Top:** a large clock.
2. **Middle:** the next upcoming meeting, prominent (title, time, maybe location or link).
3. **Below that:** the meetings after it, in smaller text.
4. **Bottom:** a countdown to the next meeting.
5. **Reserved space** for features added later (weather, notes, status light, and so on).

## Recommended Approach

- **Native Android app in Kotlin**, sideloaded over USB. No Play Store, no server.
- **Calendar data comes from the phone's own calendar** (Android `CalendarContract`). Sign the phone into the same Google/Exchange account and let Android sync it. This avoids OAuth, API keys and a backend.
- **UI in Jetpack Compose.** Android 14 supports current Compose and libraries, so there are no old-OS limits. Set `minSdk` to 34 (only this phone matters), with `targetSdk`/`compileSdk` on the latest stable SDK.
- **Layout is a vertical stack of independent sections** (clock, next meeting, upcoming list, countdown, open slot), so new features can go in as new sections.

## What You Need on the Mac

| Item | Purpose |
|---|---|
| **Android Studio** | IDE, Android SDK, emulator and bundled JDK, all in one install |
| **Android SDK Platform-Tools** (`adb`) | Installs and debugs the app on the phone. Comes with Android Studio; `brew install android-platform-tools` also works |
| **Android 14 (API 34) SDK platform** plus the latest stable one | Install them through Android Studio's SDK Manager |
| **USB-C cable** that carries data, not just charge | Connects the phone to the Mac |
| **Git** (optional) | Version control for the project |

## Phone Setup

1. Turn on Developer Options: tap "Build number" 7 times. Then turn on **USB debugging**.
2. Optional: turn on "Stay awake while charging" in Developer Options.
3. Sign into the calendar account and check that events show in the stock Calendar app.
4. Connect it to the Mac and confirm that `adb devices` lists it (accept the trust prompt on the phone).

## Build Steps (Iterate in This Order)

Each step should end with the app running on the real phone.

1. **Hello World on device.** Create an "Empty Activity" (Compose) project in Android Studio with `minSdk` 34, then install it over USB. This proves the toolchain end to end.
2. **Full-screen clock.** Large clock, updated every second. Screen stays on, immersive full-screen, fixed orientation.
3. **Static layout with fake data.** Build every section (clock, next meeting, upcoming list, countdown, reserved slot) from hardcoded meetings. Tune font sizes and spacing on the actual screen.
4. **Real calendar data.** Ask for calendar-read permission, query `CalendarContract` for today's and upcoming events, and fill the layout. Handle "no more meetings today."
5. **Live countdown and refresh.** Tick the countdown every second. Re-query the calendar periodically and when calendar data changes. Move on when a meeting starts or ends.
6. **Polish for all-day use.** Sort out all-day events, declined events, and which calendars to include. Add a dark theme and optional night dimming. Shift pixels slightly to limit burn-in. Add a "starting soon" color change.
7. **Appliance mode.** Register the app as a home launcher and pick it as the default Home, so it comes back after reboots and when Home is pressed. (Android 14 blocks apps from opening themselves in the background on boot, so being the launcher is the reliable route.) Optionally use screen pinning so the phone only ever shows the dashboard. Exclude the app from battery optimization so refreshes aren't paused.
8. **Future features.** Add them as new sections in the reserved area, one at a time.

## Risks and Open Questions

- **Calendar sync on the phone.** If the work account won't sync to the phone (for example because of corporate device-management rules), fall back to reading the Google Calendar API or an ICS feed over Wi-Fi.
- **Battery health.** A phone plugged in all the time can have its battery swell. Check whether this Moto offers a charge-limit or optimized-charging setting, and watch the battery.
- **Screen burn-in** from static content, handled in step 6.

## Out of Scope for v1

Play Store publishing, a backend server, multiple users, editing meetings from the phone.
