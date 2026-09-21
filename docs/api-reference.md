# API reference

- **Base URL:** `https://terrace.javiermateo.dev`
- **Authentication:** none
- **CORS:** open for `/api/**` (`GET`, `POST`, `OPTIONS`)
- **Content type:** `application/json`
- **Interactive documentation:** `/swagger-ui.html` · **OpenAPI spec:** `/v3/api-docs`

Conventions used throughout:

- `lat` in −90..90 and `lon` in −180..180 (decimal degrees).
- **All date-times are local time at the restaurant**, in ISO format without `Z` or offset: `2026-09-22T13:30:00` (seconds are optional: `2026-09-22T13:30`).
- Messages and error text are always in English.

---

## Shared: tolerance parameters

Accepted by every scoring endpoint. All optional. Full explanation in [Profiles and thresholds](profiles-and-thresholds.md).

| Parameter | Type | Range | Default (`TEMPERATE`) |
|---|---|---|---|
| `profile` | enum | `TEMPERATE`, `TROPICAL`, `MEDITERRANEAN`, `NORDIC` | `TEMPERATE` |
| `maxRainProbability` | integer | 0–100 | 40 |
| `maxPrecipitationMm` | number | ≥ 0 | 0.5 |
| `maxGustsKmh` | number | ≥ 0 | 35 |
| `minApparentTempC` | number | −40..50 | 14 |
| `maxApparentTempC` | number | −40..60 | 34 |

An explicit threshold always overrides the profile. `minApparentTempC` must be lower than `maxApparentTempC`.

In `POST /windows` these fields go inside an optional `rules` object instead of the query string.

## Shared: response objects

### `HourScore`

| Field | Type | Description |
|---|---|---|
| `score` | integer 0–100 | Terrace viability for the hour |
| `verdict` | `OPEN` \| `CAUTION` \| `CLOSED` | ≥ 70 / 40–69 / < 40, or `CLOSED` on a hard stop |
| `reasons` | string[] | Plain-English factors that cost points; empty if none |
| `conditions` | object | The weather used (below) |

`conditions`:

| Field | Type | Description |
|---|---|---|
| `time` | string | Start of the hour, local time |
| `temperatureC` | number | Air temperature |
| `apparentTemperatureC` | number | Feels-like temperature |
| `precipitationProbability` | integer \| null | `%`; `null` when the weather model does not provide it |
| `precipitationMm` | number | Expected precipitation in the hour |
| `windSpeedKmh` | number | Mean wind at 10 m |
| `windGustsKmh` | number | Gusts at 10 m |

### `Window`

| Field | Type | Description |
|---|---|---|
| `timezone` | string | IANA time zone of the location |
| `start`, `end` | string | The requested range, local time |
| `verdict` | enum | The **worst** verdict among its hours |
| `minScore` | integer | Lowest hourly score |
| `averageScore` | integer | Mean hourly score (rounded) |
| `recommendedCapacityPercent` | integer | `OPEN` → 100, `CAUTION` → 50, `CLOSED` → 0 |
| `reasons` | string[] | Distinct reasons across all its hours |
| `hours` | `HourScore[]` | One entry per hour in the window |

---

## `GET /api/v1/terrace/forecast`

Hour-by-hour scores starting at the **current hour at the location**.

| Parameter | Required | Description |
|---|---|---|
| `lat`, `lon` | yes | Location |
| `hours` | no | Number of hours, 1–72 (default 24) |
| tolerance parameters | no | See above |

Response: `{ "timezone": "Europe/Madrid", "hours": [ HourScore, … ] }`. Fewer hours than requested are returned if the forecast ends earlier.

## `GET /api/v1/terrace/window`

One verdict for a service window.

| Parameter | Required | Description |
|---|---|---|
| `lat`, `lon` | yes | Location |
| `start`, `end` | yes | Local date-times. `start` inclusive, `end` exclusive; `end` must be after `start` |
| tolerance parameters | no | See above |

Response: a `Window`. `400` if the range has no forecast data (the forecast covers the next 3 days).

## `POST /api/v1/terrace/windows`

Up to **25 windows** of one location, one weather lookup, one rate-limit hit.

Request body:

```json
{
  "lat": 40.4168,
  "lon": -3.7038,
  "rules": { "profile": "MEDITERRANEAN", "maxGustsKmh": 45 },
  "windows": [
    { "id": "mesa-12", "start": "2026-09-22T13:30:00", "end": "2026-09-22T15:30:00" }
  ]
}
```

| Field | Required | Description |
|---|---|---|
| `lat`, `lon` | yes | Location |
| `rules` | no | Tolerance fields (same names as the query parameters) |
| `windows` | yes | 1–25 items |
| `windows[].id` | no | Your label (max 64 chars), echoed back. Defaults to the item's position (`"0"`, `"1"`, …) |
| `windows[].start`, `.end` | yes | Local date-times, as in `window` |

Response:

```json
{
  "timezone": "Europe/Madrid",
  "results": [
    { "id": "mesa-12", "window": { "...": "a Window" } },
    { "id": "rota", "error": "'end' must be after 'start'" }
  ]
}
```

Each entry has **either** `window` **or** `error`, never both, and the HTTP status is `200` as long as the request itself is valid. A window with bad times fails alone; the rest are still evaluated. Request-level problems (invalid coordinates or thresholds, more than 25 windows, malformed JSON) return `400` for the whole call.

## `GET /api/v1/terrace/best-window`

The best window of a given length on a day, plus alternatives.

| Parameter | Required | Description |
|---|---|---|
| `lat`, `lon` | yes | Location |
| `date` | yes | ISO date, e.g. `2026-09-22` |
| `durationHours` | no | Window length, 1–12 (default 2) |
| `fromHour` | no | First hour of service, 0–23 (default 12) |
| `toHour` | no | End of service, 1–24 (default 23) |
| tolerance parameters | no | See above |

Windows start at every whole hour from `fromHour`, and must end by `toHour`. `toHour − fromHour` must be at least `durationHours`.

Response:

```json
{
  "timezone": "Asia/Bangkok",
  "date": "2026-09-22",
  "durationHours": 2,
  "best": { "...": "a Window" },
  "alternatives": [ { "...": "a Window" } ]
}
```

`alternatives` has up to 3 windows that do not overlap `best` or each other, and are never `CLOSED`. It is empty when nothing else is usable. Ranking: highest `minScore`, then `averageScore`, then earliest start. `400` if there is no complete forecast for that date.

## `GET /api/v1/terrace/profiles`

Returns every profile and its thresholds:

```json
{
  "TEMPERATE": { "maxRainProbability": 40, "maxPrecipitationMm": 0.5, "maxGustsKmh": 35.0,
                 "minApparentTempC": 14.0, "maxApparentTempC": 34.0 },
  "TROPICAL": { "maxRainProbability": 70, "maxPrecipitationMm": 1.5, "maxGustsKmh": 40.0,
                "minApparentTempC": 18.0, "maxApparentTempC": 38.0 }
}
```

*(Shortened; the real response includes `MEDITERRANEAN` and `NORDIC`.)*

## `GET /actuator/health`

Health check for uptime monitors. Returns `{"status":"UP"}`.

---

## Errors

Errors follow [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`):

```json
{
  "detail": "maxRainProbability: must be less than or equal to 100",
  "instance": "/api/v1/terrace/forecast",
  "status": 400,
  "title": "Bad Request"
}
```

`detail` is written for humans and names the offending field. Examples of what you may see:

| `detail` | Cause |
|---|---|
| `lat: must be less than or equal to 90` | Coordinate out of range |
| `start: expected a local date-time like 2026-09-22T13:00:00 (no 'Z' and no UTC offset)` | A time with `Z` or an offset such as `+02:00` |
| `date: expected a date like 2026-09-22` | Malformed date |
| `profile: invalid value 'MARS' (expected one of [TEMPERATE, TROPICAL, MEDITERRANEAN, NORDIC])` | Unknown profile |
| `minApparentTempC must be lower than maxApparentTempC` | Inconsistent thresholds |
| `windows: size must be between 1 and 25` | Empty batch or more than 25 windows |
| `No forecast data for the requested window (forecast covers the next 3 days)` | Window outside the forecast |

Times with a `Z` or a UTC offset are **rejected on purpose**: silently ignoring the offset would evaluate the wrong hours. Send the restaurant's local time.

| Status | When | Retry? |
|---|---|---|
| `400` | Invalid or missing parameter, bad threshold range, `min ≥ max` temperature, window without forecast data, more than 25 windows, malformed JSON | No: fix the request |
| `429` | More than 60 requests per minute from your IP. Includes a `Retry-After` header (seconds) | Yes, after `Retry-After` |
| `502` | The weather provider (Open-Meteo) failed or timed out | Yes, with backoff |

### Response headers

| Header | Meaning |
|---|---|
| `X-RateLimit-Limit` | Requests allowed per minute (60) |
| `X-RateLimit-Remaining` | Requests left in the current window |
| `Retry-After` | On `429`: seconds until you can retry |
