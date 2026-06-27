# Analytics

PdfMaster uses a **provider-agnostic analytics seam**. The whole app talks to the `Analytics`
interface; nothing calls a vendor SDK directly. This means you can ship today (events log to
Logcat) and switch to Firebase with a one-line binding change.

## Architecture

```
Call sites (ViewModels, FeatureGate, BillingManager)
        │  analytics.track(AnalyticsEvent.…)
        ▼
Analytics  ──►  ConsentAwareAnalytics      ← honors the opt-out, then forwards
                        │  @RawAnalytics
                        ▼
                LogcatAnalytics (default)  /  FirebaseAnalyticsTracker (when enabled)
```

- **`Analytics`** — the interface (`analytics/Analytics.kt`).
- **`AnalyticsEvent`** — typed event catalog (one place; see the dictionary below).
- **`ConsentAwareAnalytics`** — wrapper that drops all events when the user opts out.
- **`LogcatAnalytics`** — default; prints `▸ event { params }` under the `Analytics` tag.
- **`AnalyticsModule`** — Hilt bindings.

## Privacy / opt-out (already built)

- Settings → **Privacy → "Share usage analytics"** toggles `UserPreferences.analyticsEnabled`
  (default **on**).
- When off, `ConsentAwareAnalytics` forwards nothing and calls `setCollectionEnabled(false)` +
  `setUserId(null)` on the delegate.
- **No document content, file names, or PII are ever logged** — events carry only counts,
  enums, and booleans. Keep it that way when adding events.

## Enabling Firebase

> **Status: wired up.** The Firebase Analytics + Crashlytics SDKs are in the build, the
> `google-services` plugin is applied **conditionally** (only when `app/google-services.json`
> exists), `FirebaseAnalyticsTracker` is implemented, and the Hilt raw binding points at it.
> The tracker **falls back to Logcat** until the config is added (verified on-device: the app
> starts fine and still logs the funnel without `google-services.json`).
>
> **The only thing left is your Firebase project config:**
> 1. Create the Firebase project; add an Android app with package `com.pdfmaster.app`.
> 2. Download **`google-services.json`** into `app/`.
>
> That's it — the next build picks it up automatically (the conditional `apply` in
> `app/build.gradle.kts` fires) and events flow to Firebase with no code change. No call sites
> change. To deobfuscate release crash reports later, also add `apply false`'d
> `com.google.firebase.crashlytics` plugin to the conditional block.

<details><summary>Reference: how it was wired (already done)</summary>

3. **Gradle** — `app/build.gradle.kts` declares the deps and a conditional plugin apply:
     ```kotlin
     implementation(platform(libs.firebase.bom))
     implementation(libs.firebase.analytics)
     implementation(libs.firebase.crashlytics)
     // …at the bottom of the file:
     if (file("google-services.json").exists()) {
         apply(plugin = "com.google.gms.google-services")
     }
     ```
4. **The tracker** — `analytics/FirebaseAnalyticsTracker.kt` (guards on `FirebaseApp.getApps`,
   falls back to `LogcatAnalytics` when Firebase isn't configured):
   ```kotlin
   package com.pdfmaster.app.analytics

   import android.content.Context
   import android.os.Bundle
   import com.google.firebase.analytics.FirebaseAnalytics
   import com.google.firebase.crashlytics.FirebaseCrashlytics
   import dagger.hilt.android.qualifiers.ApplicationContext
   import javax.inject.Inject
   import javax.inject.Singleton

   @Singleton
   class FirebaseAnalyticsTracker @Inject constructor(
       @ApplicationContext context: Context,
   ) : Analytics {
       private val fa = FirebaseAnalytics.getInstance(context)
       private val crashlytics = FirebaseCrashlytics.getInstance()

       override fun track(event: AnalyticsEvent) {
           val bundle = Bundle().apply {
               event.params.forEach { (k, v) ->
                   when (v) {
                       null -> {}
                       is Int -> putLong(k, v.toLong())
                       is Long -> putLong(k, v)
                       is Double -> putDouble(k, v)
                       is Float -> putDouble(k, v.toDouble())
                       is Boolean -> putString(k, v.toString())
                       else -> putString(k, v.toString())
                   }
               }
           }
           fa.logEvent(event.name, bundle)
       }

       override fun setUserProperty(key: String, value: String?) = fa.setUserProperty(key, value)
       override fun setUserId(id: String?) = fa.setUserId(id)
       override fun setCollectionEnabled(enabled: Boolean) {
           fa.setAnalyticsCollectionEnabled(enabled)
           crashlytics.isCrashlyticsCollectionEnabled = enabled
       }
   }
   ```
5. **The binding** in `di/AnalyticsModule.kt` already points the raw tracker at
   `FirebaseAnalyticsTracker` (wrapped by `ConsentAwareAnalytics`, so consent keeps working).

</details>

## Event dictionary

| Event | When | Params |
|---|---|---|
| `app_open` | Process start | — |
| `onboarding_started` / `onboarding_completed` | Onboarding flow | — |
| `tool_opened` | A tool screen opens | `tool` |
| `tool_completed` | A tool finishes successfully | `tool` + tool-specific (`file_count`, `page_count`, `image_count`, sizes, `quality`) |
| `tool_failed` | A tool errors | `tool`, `reason` |
| `search_performed` | In-PDF search runs | `query_length`, `result_count` |
| `scan_completed` | A scan is saved | `page_count`, `watermarked` |
| `editor_text_edited` | Text add/edit/delete in editor | — |
| `editor_saved` | Editor save succeeds | `replace_original` |
| `gate_blocked` | **Free user hits a wall (top funnel signal)** | `feature`, `gate_type` (`premium_feature`/`daily_limit`/`count_limit`) |
| `paywall_viewed` | Premium screen opens | `source` |
| `plan_selected` | User taps a plan to buy | `plan` |
| `purchase_started` | Play sheet launched | `plan` |
| `purchase_succeeded` | Entitlement granted (new) | — |
| `purchase_cancelled` | User cancels the sheet | — |
| `purchase_failed` | Billing error | `reason` |
| `purchase_restored` | Restore completes | `is_premium` |

**User property:** `is_premium` (`true`/`false`) — set whenever entitlement changes.

### The conversion funnel to watch
`app_open → tool_opened → tool_completed (activation)` then
`gate_blocked → paywall_viewed → plan_selected → purchase_started → purchase_succeeded`.
`gate_blocked` segmented by `feature` tells you **which wall drives the most upgrades** — point
your pricing/limit tuning there.

## Play Store Data safety

Because analytics is on by default, the Play listing's **Data safety** form must declare it.
See `PLAY_STORE_LISTING.md` for the exact answers (app activity / analytics, not linked to
identity, not shared, user can opt out).
