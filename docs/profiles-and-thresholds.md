# Profiles and thresholds

The score compares the forecast with **what your terrace tolerates**. Those tolerances are five thresholds, and a *profile* is a ready-made set of them for a type of climate.

## The five thresholds

| Parameter | Meaning | Unit |
|---|---|---|
| `maxRainProbability` | Rain chance above which points start to be lost | % (0–100) |
| `maxPrecipitationMm` | Expected rain at which the terrace closes outright | mm per hour |
| `maxGustsKmh` | Gusts above which points are lost (1.5× closes outright) | km/h |
| `minApparentTempC` | Feels-like temperature below which it is too cold | °C |
| `maxApparentTempC` | Feels-like temperature above which it is too hot | °C |

`minApparentTempC` must be lower than `maxApparentTempC`, otherwise the request is rejected with a `400`.

## Profiles

`GET /api/v1/terrace/profiles` returns the live values. At the time of writing:

| Profile | Rain % | Precip. mm | Gusts km/h | Min °C | Max °C | Fits |
|---|---|---|---|---|---|---|
| `TEMPERATE` (default) | 40 | 0.5 | 35 | 14 | 34 | Mild climates, most of Europe and North America |
| `MEDITERRANEAN` | 30 | 0.3 | 35 | 15 | 38 | Dry, hot summers: rain is rare and unwelcome, heat is expected |
| `TROPICAL` | 70 | 1.5 | 40 | 18 | 38 | Hot, humid and showery: rain is routine, awnings and fans are common |
| `NORDIC` | 50 | 0.8 | 35 | 8 | 30 | Cool climates: customers accept cold and drizzle, but not heat |

Use a profile as the **base** and override only what differs:

```
/api/v1/terrace/window?lat=41.39&lon=2.17&start=2026-09-22T13:00:00&end=2026-09-22T16:00:00
    &profile=MEDITERRANEAN&maxGustsKmh=45
```

Explicit parameters always win over the profile. In `POST /windows` the same fields go inside the `rules` object:

```json
{ "rules": { "profile": "MEDITERRANEAN", "maxGustsKmh": 45 }, ... }
```

## Same hour, different climate

Real samples for Bangkok on 22 September 2026 (values will differ on other days):

| Service | `TEMPERATE` | `TROPICAL` |
|---|---|---|
| Lunch 12:00–15:00 | `CLOSED`, min score 19: rain 74–87 % and 37–39 °C feels-like | `CAUTION`, min score 58: only rain and one hour of heat above 38 °C |
| Dinner 19:00–22:00 | `CLOSED`: 2.7 mm expected | `CLOSED`: 2.7 mm is above even the 1.5 mm tropical limit |

With the default profile, a Bangkok restaurant would never open its terrace; with `TROPICAL`, lunch becomes a manageable `CAUTION` (offer indoor as backup) while a genuinely rainy dinner still closes. That is the behaviour you want from a profile: relaxed for what is normal there, strict for what is truly bad.

## Tune it to your own terrace

Profiles describe a climate; **your equipment changes the picture**. Start from the closest profile and adjust:

| You have… | Adjust | Why |
|---|---|---|
| A retractable awning or roof | Raise `maxRainProbability` and `maxPrecipitationMm` | Light rain no longer reaches the tables |
| Patio heaters or blankets | Lower `minApparentTempC` | Customers stay comfortable when it is cold |
| Misting fans, shade sails, air-cooled zones | Raise `maxApparentTempC` | Heat is tolerable |
| Windbreaks or a sheltered courtyard | Raise `maxGustsKmh` | Gusts are damped at table level |
| Loose parasols and lightweight furniture | Lower `maxGustsKmh` | Gusts become a safety issue sooner |
| A covered area *and* an open area | Call the API twice with different thresholds | Sell the covered tables when the open ones close |

### A simple calibration method

1. Start with the nearest profile.
2. For two weeks, note each day whether the API's verdict matched what you would have decided after looking at the sky.
3. If the API is **too cautious** (you kept the terrace open when it said `CLOSED`/`CAUTION`), raise the threshold of the factor named in `reasons`.
4. If it is **too optimistic** (you had to clear tables it said `OPEN`), lower that threshold.
5. Change one threshold at a time and re-check; `reasons` tells you which factor to touch.

Because the `reasons` list names the factor behind every lost point, calibration is a matter of reading them rather than guessing.

## Choosing thresholds for the customer experience

Thresholds are also a **business decision**, not only a weather one:

- A **fine-dining** terrace should tolerate less (lower rain and gust limits): one wet plate ruins a €80 dinner.
- A **casual bar** can tolerate more: customers accept a drizzle and a jacket.
- If clearing tables is **expensive** (large terrace, few staff), be more cautious and decide earlier using `best-window` and the 24-hour forecast.
