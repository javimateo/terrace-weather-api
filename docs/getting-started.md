# Getting started

This guide takes you from zero to a working integration in a few minutes. No account or API key is needed.

- **Base URL:** `https://terrace.javiermateo.dev`
- **Running locally:** `http://localhost:8080` (see the [README](../README.md#run-it-yourself))
- **Format:** JSON over HTTPS. Errors use `application/problem+json`.

## 1. Find your coordinates

Every call needs the restaurant's `lat` and `lon` (decimal degrees). The quickest way: in Google Maps, right-click your venue and click the coordinates at the top of the menu to copy them (e.g. `40.4168, -3.7038` for Madrid's centre).

Precision to two decimals (about 1 km) is plenty: weather barely changes over a few hundred metres, and the API caches by that grid cell.

## 2. Ask for the next hours

```bash
curl "https://terrace.javiermateo.dev/api/v1/terrace/forecast?lat=40.4168&lon=-3.7038&hours=2"
```

```json
{
  "timezone": "Europe/Madrid",
  "hours": [
    {
      "score": 100,
      "verdict": "OPEN",
      "reasons": [],
      "conditions": {
        "time": "2026-09-21T13:00:00",
        "temperatureC": 27.1,
        "apparentTemperatureC": 26.6,
        "precipitationProbability": 0,
        "precipitationMm": 0.0,
        "windSpeedKmh": 1.5,
        "windGustsKmh": 9.0
      }
    }
  ]
}
```

*(Sample response, shortened to one hour. Your values will differ.)*

## 3. Read the answer

| Field | Meaning |
|---|---|
| `verdict` | `OPEN`: sell terrace tables normally. `CAUTION`: possible but risky, keep an indoor fallback. `CLOSED`: don't sell terrace tables. |
| `score` | 0–100 viability. ≥ 70 is `OPEN`, 40–69 `CAUTION`, below 40 `CLOSED`. |
| `reasons` | Why points were lost, in plain English. Empty means nothing bothered the score. |
| `conditions` | The raw weather the score was computed from, so you can show it to staff or customers. |
| `timezone` | The location's time zone. **All times in requests and responses are local time at the restaurant** (no `Z`, no offset). |

For a service window the response also includes `recommendedCapacityPercent`: `OPEN` → 100, `CAUTION` → 50, `CLOSED` → 0.

## 4. Ask about a service window

Restaurants think in shifts, not hours. `window` gives one verdict for a range and uses its **worst hour** — one storm in the middle of lunch is enough to close the terrace.

```bash
curl "https://terrace.javiermateo.dev/api/v1/terrace/window?lat=40.4168&lon=-3.7038&start=2026-09-22T13:00:00&end=2026-09-22T16:00:00"
```

`start` is included and `end` is excluded, so `13:00–16:00` covers the 13:00, 14:00 and 15:00 hours. A `start` such as `13:30` still includes the 13:00 hour it falls in.

## 5. Adapt it to your climate

Defaults suit mild climates. In a hot, showery city they close the terrace far too often. Pick a profile:

```bash
curl "https://terrace.javiermateo.dev/api/v1/terrace/window?lat=13.7563&lon=100.5018&start=2026-09-22T12:00:00&end=2026-09-22T15:00:00&profile=TROPICAL"
```

Profiles are starting points; every threshold can be overridden individually. See [Profiles and thresholds](profiles-and-thresholds.md).

## 6. Check a whole day at once

Have ten reservations? Send them in one request (up to 25):

```bash
curl -X POST "https://terrace.javiermateo.dev/api/v1/terrace/windows" \
  -H "Content-Type: application/json" \
  -d '{
        "lat": 40.4168, "lon": -3.7038,
        "rules": { "profile": "MEDITERRANEAN" },
        "windows": [
          { "id": "mesa-12", "start": "2026-09-22T13:30:00", "end": "2026-09-22T15:30:00" },
          { "id": "mesa-7",  "start": "2026-09-22T21:00:00", "end": "2026-09-22T23:00:00" }
        ]
      }'
```

Each result carries your `id` back, so you can match it to the reservation. See the [integration recipes](integration-recipes.md) for a complete script.

## Next steps

- Understand every factor: [How the score works](scoring.md)
- Copy a working integration: [Integration recipes](integration-recipes.md)
- Look up a parameter: [API reference](api-reference.md)
