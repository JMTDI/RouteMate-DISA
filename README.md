# RouteMate

An Android app for routing outgoing calls through a DISA (Direct Inward System Access) PBX number. When you dial any number, RouteMate intercepts the call *before* it connects and gives you a choice: route it through your corporate PBX (automatically entering your PIN and destination number), place it directly, or cancel.

---

## Features

- Intercepts outgoing calls before they connect — the other phone never rings
- Overlay popup appears immediately when you press Call
- Three choices: **Via RouteMate** (DISA re-dial), **Direct Call**, **Cancel**
- Automatically dials DISA number → waits → enters PIN → waits → enters destination
- Configurable pause timings for PIN and destination entry
- Full hardware key support on flip phones and feature phones:
  - D-pad navigates between buttons
  - **Call key** activates the focused button
  - **End call key** or **Power key** cancels

---

## Requirements

- Android 8.0 (API 26) or higher
- A DISA-enabled PBX or telephone system

---

## Setup

### 1. Install the APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Grant permissions via ADB

```bash
# Runtime permissions
adb shell pm grant com.routemate.app android.permission.CALL_PHONE
adb shell pm grant com.routemate.app android.permission.READ_PHONE_STATE
adb shell pm grant com.routemate.app android.permission.ANSWER_PHONE_CALLS
adb shell pm grant com.routemate.app android.permission.PROCESS_OUTGOING_CALLS

# Draw over other apps
adb shell appops set com.routemate.app SYSTEM_ALERT_WINDOW allow

# Accessibility service
adb shell settings put secure enabled_accessibility_services \
  com.routemate.app/com.routemate.app.RouteMateAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Or grant them manually in the app's **Settings** screen.

### 3. Configure DISA settings

Open the app and fill in:

| Field | Description |
|-------|-------------|
| **DISA Number** | The phone number of your PBX/DISA line |
| **PIN** | Your DISA access PIN (digits only) |
| **Pause after dial** | Seconds to wait before sending the PIN (default 2s) |
| **Pause after PIN** | Seconds to wait before sending the destination number (default 2s) |

Tap **Save Settings**.

---

## How It Works

1. You dial any number in your phone's dialer and press Call
2. Android fires the `NEW_OUTGOING_CALL` broadcast — RouteMate's `OutgoingCallReceiver` receives it with priority 999, **cancels the call**, and shows the overlay popup
3. You choose:
   - **Via RouteMate** — dials your DISA number with DTMF tones: `{DISA},{PIN}#,{destination}#`
   - **Direct Call** — re-places the original call normally
   - **Cancel** — does nothing, call is dropped
4. The `suppressNextCall` flag ensures RouteMate's own re-dialled calls are never intercepted again

### Dial string format

```
{disa_number}{initial_pause}{pin}#{dest_pause}{destination}#
```

Commas (`,`) are DTMF pause characters (≈1 second each). `#` signals end of each entry.  
`Uri.fromParts("tel", dialString, null)` is used instead of `Uri.parse()` to prevent `#` being treated as a URI fragment separator.

---

## Permissions

| Permission | Purpose |
|-----------|---------|
| `CALL_PHONE` | Place calls via TelecomManager |
| `READ_PHONE_STATE` | Read phone state |
| `ANSWER_PHONE_CALLS` | End active calls (API 28+) |
| `PROCESS_OUTGOING_CALLS` | Intercept and cancel outgoing calls |
| `SYSTEM_ALERT_WINDOW` | Draw the overlay popup over other apps |
| `BIND_ACCESSIBILITY_SERVICE` | Intercept hardware keys (Call, End, Power) |

---

## Building

Requirements: Android SDK, Java 21, Gradle 8.12

```bash
# First time — copy your SDK path
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

---

## Logcat

```bash
adb logcat -v threadtime | grep -E "com\.routemate\.app|RouteMate|OverlayManager"
```

---

## Project Structure

```
app/src/main/
├── java/com/routemate/app/
│   ├── MainActivity.kt                 # Settings screen
│   ├── OutgoingCallReceiver.kt         # Intercepts NEW_OUTGOING_CALL broadcast
│   ├── OverlayManager.kt               # Floating popup window
│   ├── PopupRootLayout.kt              # Custom root view for key dispatch
│   └── RouteMateAccessibilityService.kt# Hardware key interception
├── res/
│   ├── layout/
│   │   ├── activity_main.xml
│   │   └── overlay_popup.xml
│   └── xml/
│       └── accessibility_service_config.xml
└── AndroidManifest.xml
```
