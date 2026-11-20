# Ads analytics: events, KPIs and pull-back guardrails

Owner: **Monetisation / Ram**. Dashboards and BigQuery queries belong to the Analytics Manager and are built on the events below.

Rule for every event: no PII, recipe titles, ingredient text, notes, shopping-list contents or import URLs. Params are product ids, price micros, currency, source enums and result flags only.

## Event spec

Constants live in `analytics/Analytics.kt` (`AnalyticsEvents`, `AnalyticsParams`).

| Event | When | Params |
|---|---|---|
| `remove_ads_paywall_view` | The Remove ads row in Settings is shown (once per Settings visit, only after entitlement has resolved; not shown or logged for Pro / Remove ads owners) | none |
| `ad_paywall_entry` | User taps an entry into the Remove ads offer | `source`: `settings_row` (only entry today; `interstitial_close`, `banner_overflow` are reserved) |
| `remove_ads_purchase_start` | Play purchase sheet launched for `remove_ads` | `product_id`, `price_micros`, `currency` |
| `remove_ads_purchase_success` | A fresh `remove_ads` purchase completes in the purchase flow. **Never on Restore / refresh**, and not on a repeated delivery | `product_id`, `price_micros`, `currency` |
| `remove_ads_purchase_fail` | Cancel, billing error, or sheet could not launch | `product_id`, `price_micros`, `currency`, `result` (`canceled` / `error` / `launch_failed`). Not logged after `ITEM_ALREADY_OWNED` or a PENDING result, which end the flow |

`price_micros` and `currency` come from Play's product details and are omitted until Play has reported them.

Existing Pro events are unchanged and still fire: `pro_screen_view`, `purchase_started`, `purchase` (with `value` / `currency`), `purchase_restored`. `purchase_started`, `purchase` and `purchase_restored` also carry `product_id` (the Pro product id); `pro_screen_view` has no params. `purchase` is still a fresh buy only; `purchase_restored` covers Restore.

### `ad_impression` (automatic)

Firebase logs `ad_impression` (with `value` / `currency`) automatically for AdMob ads once the Firebase project is linked to the AdMob app. The app does nothing special, and ad revenue is **not** logged by hand (that would double count). Dependency: Studio issue 00 (Firebase to AdMob link, Release and QA). Until the link exists, expect no `ad_impression` events in GA4; that is a missing link, not a bug.

## KPIs

Compare against the baseline (see below) and keep watching the absolute series.

1. **ARPDAU**: (ad revenue + IAP revenue) / DAU, with ads and IAP split out.
2. **Ad impressions per DAU** and **estimated ad revenue per DAU** (from `ad_impression`).
3. **Remove ads conversion**: `remove_ads_purchase_success` / `remove_ads_paywall_view`.
4. **Pro conversion**: `purchase` / `pro_screen_view`. Must not collapse.
5. **Retention** D1 / D7 / D30.
6. **Play rating, 1-star rate**, and a count of reviews that mention ads (manual tag in Play Console).
7. **Crash-free users and ANR rate** versus the app's halt criteria (Crashlytics / Play vitals).

## Baseline: required recording step

Spoonfile may ship ads from its first build, so there may be no long no-ads production cohort. Do **not** wait for a fictional one. Before any rollout wider than internal testing, **freeze and record** whatever internal-test metrics exist (items 1 to 7 above, plus sample sizes and dates) in the release notes or this file. If the window is empty, say so and use the absolute series. Guardrails 1 and 2 are only judged with a usable sample (no judging on single-digit purchases).

## Pull-back guardrails (ads plan section 4.3)

If **any** trips, tighten or kill ads the same day and tell Ram:

1. D1 or D7 retention is 5 or more percentage points below the baseline after at least 7 days and a usable sample.
2. Pro conversion (purchases / paywall views) is 25% or more below the baseline, with a usable sample.
3. Crash or ANR rate hits the halt threshold, or a crash cluster is in the ads SDK.
4. A visible spike in 1-star reviews or support mail about ads, frequency or accidental clicks.
5. Interstitial fill that appears on open / exit, on a form, or stacked. This is a policy bug: set `ads_interstitial_enabled=false` immediately.

### Remote Config kill switches (no Play release needed)

Keys are defined in `ads/AdsConfig.kt` (`AdsConfigKeys`); launch defaults are the in-app defaults there (sf-01) and must match what is published in the Firebase console.

| Order | Key | Launch default | Set to | Effect |
|---|---|---|---|---|
| First pull-back | `ads_interstitial_enabled` | `true` | `false` | Interstitials stop; banners stay |
| Second pull-back | `ads_enabled` | `true` | `false` | All ads stop |

- **Who sets them:** Monetisation / Ram, in Firebase Remote Config (publish takes effect on the next fetch; the app fetches at most hourly and on foreground).
- **Turning ads back on** needs Ram, then Ajith, after the cause is fixed.
- Other tuning keys (`ads_interstitial_max_per_day`, `ads_interstitial_min_interval_sec`, and so on) can be tightened as a softer step; changing placements or default caps from data is Monetisation's job via RC.
