# Integration recipes

Practical patterns for connecting the API to a reservation system, a dashboard or a messaging flow. Every script here is a real file in [`docs/examples`](examples/) and is tested against the API.

Set `TERRACE_API_URL` to point them at a local instance:

```bash
TERRACE_API_URL=http://localhost:8080 node morning-check.mjs
```

Requirements: Node 18+ for the JavaScript examples (built-in `fetch`), JDK 11+ for the Java one.

---

## Recipe 1: Morning check of the day's reservations

**Goal:** every morning, look at each terrace reservation of the day and decide which ones to keep outdoors, warn, or move indoors.

**Approach:** turn each reservation into a window (`start` = reservation time, `end` = start + expected duration) and send them **all in one `POST /windows`** (up to 25). It counts as a single request for the [rate limit](limits-and-best-practices.md) and needs one weather lookup, however many reservations you send.

**File:** [`examples/morning-check.mjs`](examples/morning-check.mjs)

```bash
node morning-check.mjs 2026-09-22
```

Sample output (Madrid):

```
R-1041 (Garcia, 4 pax): OPEN (score 100) -> Keep the terrace table
R-1042 (Lopez, 2 pax): OPEN (score 100) -> Keep the terrace table
R-1050 (Martin, 6 pax): OPEN (score 100) -> Keep the terrace table
```

The core of it:

```js
const response = await fetch(`${BASE_URL}/api/v1/terrace/windows`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    lat, lon,
    rules: { profile: 'MEDITERRANEAN' },
    windows: reservations.map((r) => ({ id: r.id, start: r.start, end: r.end })),
  }),
});
const { results } = await response.json();
```

**Things to remember**

- **Use your reservation id as `id`.** It comes back untouched, so matching results to reservations is a lookup, not a guess.
- **A window can fail on its own.** Each entry in `results` has either `window` or `error` (for example a time outside the 3-day forecast). Handle both, as the example does.
- **Times are local to the restaurant**, not UTC.
- More than 25 reservations? Split them into batches of 25.

**Turn verdicts into actions**

| Verdict | Suggested action |
|---|---|
| `OPEN` | Keep the terrace table |
| `CAUTION` | Keep it, but tell the customer there is an indoor fallback; re-check closer to the time |
| `CLOSED` | Move to indoor, or offer to reschedule |

`recommendedCapacityPercent` (100 / 50 / 0) is also handy for **capping how many terrace tables you sell for a shift** before reservations even exist.

---

## Recipe 2: Propose the best time to a customer

**Goal:** a customer wants the terrace tomorrow. Suggest the best slot, and alternatives.

**File:** [`examples/propose-time.mjs`](examples/propose-time.mjs)

```bash
node propose-time.mjs 2026-09-22 2
```

Sample output (Bangkok, `TROPICAL` profile):

```
Best terrace slot on 2026-09-22: 12:00-14:00 (CAUTION, score 63)
Other options: 21:00-23:00 (CAUTION), 14:00-16:00 (CAUTION)
```

It calls `best-window` with your **service hours** (`fromHour`, `toHour`) so it never suggests a slot when you are closed:

```
GET /api/v1/terrace/best-window?lat=13.7563&lon=100.5018&date=2026-09-22
    &durationHours=2&fromHour=12&toHour=23&profile=TROPICAL
```

**Things to remember**

- If `best.verdict` is `CLOSED`, there is no outdoor slot worth selling that day: suggest the indoor room (the example does).
- Alternatives never overlap the best slot and are never `CLOSED`, so you can read them out as-is.
- `durationHours` is how long the customer will stay (1–12).

---

## Recipe 3: A live "terrace status" for your dashboard

**Goal:** a small widget showing whether the terrace is open now and how the next hours look.

```bash
curl "https://terrace.javiermateo.dev/api/v1/terrace/forecast?lat=40.4168&lon=-3.7038&hours=6"
```

Render one cell per hour, coloured by `verdict`, with `reasons` as the tooltip:

| Verdict | Colour hint |
|---|---|
| `OPEN` | green |
| `CAUTION` | amber |
| `CLOSED` | red |

**Things to remember**

- The forecast starts at the **current hour at the restaurant's location**, so `hours[0]` is "now".
- Refresh **every 15 minutes at most**: the API caches upstream data for 15 minutes, so polling faster returns the same values and wastes your [rate limit](limits-and-best-practices.md).
- From a browser you can call the API directly: CORS is enabled and no key is required.

---

## Recipe 4: Handle rate limits and outages gracefully

**Goal:** a client that survives a `429` or a temporary weather-provider outage.

**File:** [`examples/resilient-client.mjs`](examples/resilient-client.mjs)

```js
import { terraceRequest } from './resilient-client.mjs';

const data = await terraceRequest('/api/v1/terrace/forecast?lat=40.4168&lon=-3.7038&hours=3');
```

The policy it implements:

| Status | Meaning | What to do |
|---|---|---|
| `429` | Rate limit exceeded | Wait for the `Retry-After` header (seconds), then retry |
| `502` | Weather provider temporarily unavailable | Retry with exponential backoff, a few times |
| `400` | Your request is invalid | **Never retry**: fix it. `detail` names the field |
| `200` | OK | — |

**Fail safe:** if the API is unreachable, decide what your system does by default. For a terrace the safe default is usually *treat as `CAUTION`* (keep the booking, keep an indoor table free) rather than blocking sales entirely.

---

## Recipe 5: Java client

**File:** [`examples/TerraceCheck.java`](examples/TerraceCheck.java) (JDK 11+, no dependencies)

```bash
java TerraceCheck.java 2026-09-22T13:00:00 2026-09-22T16:00:00
```

```
HTTP 200
Rate limit remaining: 59
{"timezone":"Europe/Madrid","start":"2026-09-22T13:00:00","end":"2026-09-22T16:00:00","verdict":"OPEN", ...
```

It sets connect and request timeouts (always do that when calling a network API) and reads the `X-RateLimit-Remaining` header. In a real application, deserialise the response with Jackson or Gson into your own classes:

```java
record Window(String verdict, int minScore, int recommendedCapacityPercent, List<String> reasons) {}
```

---

## Recipe 6: Notify a customer when their slot gets worse

The API is stateless (it does not push notifications), but a scheduled job gets you the same effect:

1. Every 30–60 minutes, run the [morning check](#recipe-1-morning-check-of-the-days-reservations) for the reservations of the next 24 hours.
2. Keep the last verdict per reservation id.
3. When it changes to a worse one (`OPEN` → `CAUTION`/`CLOSED`), send the customer a message: *"The forecast shows rain at 14:00. Would you like us to keep your table indoors?"*
4. Prefer to alert early (the day before, then a few hours ahead) rather than at the last minute.

Webhook-based alerts are on the [roadmap](../README.md#roadmap).
