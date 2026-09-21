# Terrace Weather API

**Should the terrace be open tonight?** A free, keyless API that turns the weather forecast into a decision for restaurants: an hour-by-hour **terrace viability score**, a verdict (`OPEN` / `CAUTION` / `CLOSED`) and a suggested terrace capacity, so reservations can be moved indoors *before* the rain arrives.

```bash
curl "https://terrace.javiermateo.dev/api/v1/terrace/window?lat=40.4168&lon=-3.7038&start=2026-09-22T13:00:00&end=2026-09-22T16:00:00"
```

```json
{
  "timezone": "Europe/Madrid",
  "verdict": "OPEN",
  "minScore": 100,
  "averageScore": 100,
  "recommendedCapacityPercent": 100,
  "reasons": [],
  "hours": [ ... ]
}
```

No API key, no sign-up, CORS enabled. Weather comes from [Open-Meteo](https://open-meteo.com/); the value this API adds is the **decision layer** on top of it.

## Why not just read a weather forecast?

A forecast says "60% rain, 33 °C, gusts 24 km/h". A terrace manager needs to know *"can I sell those tables?"* This API answers that, and it:

- weighs **rain, measurable precipitation, wind gusts and feels-like temperature** with rules you can inspect ([how the score works](docs/scoring.md));
- adapts to the **climate and equipment** of each restaurant through profiles and thresholds ([profiles](docs/profiles-and-thresholds.md)) — 60% rain is routine in Bangkok and a red flag in Madrid;
- checks **a whole day of reservations in one call** (up to 25 windows);
- proposes the **best time slot** and alternatives when today's slot is bad.

## What you can do

| Endpoint | Use it to |
|---|---|
| `GET /api/v1/terrace/forecast` | Show the terrace status hour by hour (next 72 h) |
| `GET /api/v1/terrace/window` | Get one verdict for a service window, e.g. lunch 13:00–16:00 |
| `POST /api/v1/terrace/windows` | Check every reservation of a day in a single call |
| `GET /api/v1/terrace/best-window` | Find the best slot of a day and offer alternatives |
| `GET /api/v1/terrace/profiles` | List the climate presets |

Interactive docs: **`/swagger-ui.html`** · OpenAPI spec: **`/v3/api-docs`**

## Documentation

| Guide | What's inside |
|---|---|
| [Getting started](docs/getting-started.md) | Your first call in two minutes and how to read the answer |
| [How the score works](docs/scoring.md) | Every factor, the formulas and ten worked examples |
| [Profiles and thresholds](docs/profiles-and-thresholds.md) | Tune the API to your climate, awnings, heaters and fans |
| [Integration recipes](docs/integration-recipes.md) | Morning check, propose a time, live dashboard — in JavaScript, Java and curl |
| [API reference](docs/api-reference.md) | Every parameter, response field and error |
| [Limits and best practices](docs/limits-and-best-practices.md) | Rate limits, caching, retries, data attribution |
| [FAQ and glossary](docs/faq.md) | Common questions and limitations |

Runnable, tested examples live in [`docs/examples`](docs/examples).

## Run it yourself

Requires JDK 21.

```bash
./mvnw spring-boot:run     # http://localhost:8080/swagger-ui.html
./mvnw test
```

With Docker:

```bash
docker build -t terrace-weather-api .
docker run -p 8080:8080 terrace-weather-api
```

### Architecture

```
api/      REST controllers, validation, error mapping (RFC 9457 problem+json), rate limiting, CORS
scoring/  TerraceScorer (pure rules) and TerraceService (forecast, window, batch and best-window use cases)
weather/  WeatherProvider port + OpenMeteoWeatherProvider (timeouts, bounded LRU cache)
```

`WeatherProvider` is an interface, so other sources (AEMET, Tomorrow.io) can be plugged in without touching the scoring.

## Data and license

Weather data by [Open-Meteo.com](https://open-meteo.com/) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The free Open-Meteo API is for non-commercial use. Code under the [MIT License](LICENSE).

## Roadmap

Per-restaurant saved rules, webhook alerts when a verdict changes, AEMET provider, city-name lookup.
