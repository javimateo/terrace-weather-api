# Limits and best practices

This is a free, community-friendly API running on modest infrastructure, and it sits on top of a free weather service. A few habits keep it fast for everyone, including you.

## Limits

| Limit | Value |
|---|---|
| Requests | **60 per minute per IP address** |
| Windows per batch (`POST /windows`) | **25** |
| Forecast horizon | **3 days** (72 hours from the current hour) |
| Hours per `forecast` call | 72 |
| Best-window length | 1–12 hours |

Every `/api/**` response carries `X-RateLimit-Limit` and `X-RateLimit-Remaining`. Over the limit you get `429` with a `Retry-After` header.

There is **no API key** and **no SLA**: the service is provided as is, on a best-effort basis. If your business depends on it, plan a fallback (see [Recipe 4](integration-recipes.md#recipe-4-handle-rate-limits-and-outages-gracefully)) or [run your own instance](../README.md#run-it-yourself).

## Get the most from each request

### Batch instead of looping

Checking ten reservations with ten `window` calls costs ten requests and ten weather lookups. One `POST /windows` costs **one** of each. Always prefer the batch for anything more than two or three windows.

### Don't poll faster than the data changes

Weather models update every hour or so, and the API caches upstream data **for 15 minutes** per ~1 km grid cell. Polling every 15 minutes gives you everything; polling every 5 seconds gives you the same answer and burns your limit.

| Use case | Sensible refresh |
|---|---|
| Dashboard | every 15 minutes |
| Morning reservation check | once, early, plus once before service |
| Customer-facing "is the terrace open?" | server-side cache of 10–15 minutes shared by all customers |

**For a public "is the terrace open?" widget, cache on your own server.** The limit is per IP, so browsers calling the API directly each have their own allowance and it works; but one server-side call shared by all visitors is faster, keeps your page working if the API is slow or down, and lets you decide what customers see when it fails.

### Use stable coordinates

The cache key is the location rounded to **two decimals**. Send the same coordinates every time for a venue (store them once) so calls share the cache.

### Ask for what you need

`hours=6` is cheaper to process and easier to render than `hours=72`. Use `best-window` with real service hours (`fromHour`, `toHour`) instead of downloading a full day and searching yourself.

## Time zones

Every date-time you send and receive is **local time at the restaurant's coordinates**. The response includes `timezone` so you can confirm it. Do not send UTC or offsets (`Z`, `+02:00`): they are rejected with a `400` rather than silently evaluating the wrong hours.

If your reservation system stores UTC, convert to the venue's zone first (in JavaScript: `toLocaleString('sv-SE', { timeZone })`, in Java: `ZonedDateTime.withZoneSameInstant`).

## Errors and retries

| Status | Action |
|---|---|
| `400` | Bug in your request. Do not retry; log `detail` |
| `429` | Wait `Retry-After` seconds, then retry once |
| `502` | Retry with exponential backoff (0.5 s, 1 s, 2 s…), give up after 3–4 attempts |
| Network error / timeout | Same as `502`. Always set your own timeouts (5 s connect, 15 s read is reasonable) |

Decide up front what your system does when the API is unavailable. For a terrace the safe default is to treat the hour as `CAUTION`: keep the booking and keep an indoor table free, rather than blocking sales or promising a dry terrace.

## Forecast quality

- The next **12–24 hours** are considerably more reliable than day 3.
- Re-check reservations **the morning of service** even if you checked them the day before.
- A `CAUTION` verdict is a legitimate answer, not a failure: it means "decide later, with a fallback ready".
- For borderline scores (around 70 or 40) the verdict can flip with small forecast changes.

## Responsible use and attribution

- Weather data comes from [Open-Meteo.com](https://open-meteo.com/) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). If you display data derived from this API publicly, credit **Open-Meteo.com**.
- The free Open-Meteo API is for **non-commercial use**. If you plan a commercial product on top of this API, run your own instance with an [Open-Meteo commercial plan](https://open-meteo.com/en/pricing) or contact them about your usage.
- Don't try to work around the rate limit with many IP addresses. If you need higher limits, run your own instance: it is MIT-licensed.
