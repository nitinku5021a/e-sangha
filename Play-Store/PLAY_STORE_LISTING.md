# Play Store listing copy

Paste the matching `.txt` files into Play Console. Limits are noted below.

Package name: `com.digital.meditationsangha` (new listing; not Vipassana Timer’s `com.digital.sanghaworld`)

---

## App name
**Limit: 30 characters**

File: `title.txt`

```
Meditation Sangha
```

(17 characters)

---

## Short description
**Limit: 80 characters**

File: `short-description.txt`

```
Silent sit timer with gongs, awareness reminders, and a private daily log.
```

(75 characters)

---

## Full description
**Limit: 4,000 characters**

File: `full-description.txt`

Use the full file as written.

---

## What’s new (this release)
**Limit: 500 characters per language**

File: `whats-new.txt`

Use after you upload a new AAB. Bump `versionCode` in `app/build.gradle.kts` if this is a new store version (currently versionName 1.0, versionCode 1 — first Meditation Sangha listing).

---

## Store details (Console fields)

| Field | Suggested value |
| --- | --- |
| App category | Health & Fitness |
| Tags | Meditation, Timer, Mindfulness |
| Email (required) | Your developer email |
| Website | Optional |
| Privacy policy URL | Required if you collect any data; still recommended. State that the sit log stays on the device and donations go through Google Play. |
| Contains ads | No |
| In-app products | Yes (voluntary donations: `donation_unit`) |
| Target audience | 18+ is safest; content is not for children |
| News app | No |
| COVID-19 contact tracing | No |

Suggested tags (Play lets you pick a few): Meditation, Wellness, Health.

---

## Data safety (Play Console questionnaire)

Use this as a worksheet. Confirm against your own account.

| Question | Answer |
| --- | --- |
| Collects user data | No personal account. Sit times are stored **on the device only**. |
| Shared with third parties | No, except Google Play if the user donates (Play Billing). |
| Encrypted in transit | N/A for the log (local). Play donations use Google’s payment flow. |
| Users can request deletion | Yes — they can delete a day in Meditation log, or uninstall. |
| Data collected | None required to use the app. Optional: purchase tokens handled by Google Play if they donate. |
| Location | Not collected |
| Personal info (name, email) | Not collected by the app |
| Photos / files | Not collected |
| Health | Sit duration is a local log only, not connected to Health Connect |
| Ads | No |
| Analytics / crash | None built in |
| Approximate location | No |

---

## Content rating questionnaire (hints)

This is a utility timer.

- No violence, sexual content, or language
- No user-to-user communication
- Purchases: optional donations only
- Not a social network

Expected rating: **Everyone** or **PEGI 3**, depending on IARC.

---

## Screenshot captions (optional, under each screenshot)

Play allows a short caption. Suggested order if you capture screens:

1. Home — Choose a sit length  
2. Timer — Sit in silence  
3. Gong sound — Preview and choose a gong  
4. Meditation log — Daily totals on your device  
5. Be aware always — Gentle gongs through the day  
6. Donate — Voluntary support through Google Play  

Do not generate or upload images from this folder; shoot the live app.

---

## Disclaimer (already in the full description)

Keep this idea in the listing: the app is **not** an official product of any Vipassana center or organization.

---

## Files in this folder

| File | Play Console field |
| --- | --- |
| `title.txt` | App name |
| `short-description.txt` | Short description |
| `full-description.txt` | Full description |
| `whats-new.txt` | Release notes / What’s new |
| `PLAY_STORE_LISTING.md` | This guide |
