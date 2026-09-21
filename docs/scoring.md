# How the score works

The score answers one question: **how comfortable and safe is it to serve customers outdoors during this hour?** It is deliberately simple and transparent, so you can predict it, explain it to staff and tune it.

Every hour starts at **100** and loses points for each factor that goes beyond what your restaurant tolerates. The final value is the score (0–100), and the `reasons` field tells you which factors cost points.

## The four factors

| Factor | Why it matters | Measured as |
|---|---|---|
| **Rain probability** | The chance customers get wet and the terrace has to be cleared | `%` for the hour |
| **Precipitation amount** | Rain that is actually expected, not just possible | `mm` in the hour |
| **Wind gusts** | Gusts, not average wind, knock over parasols, glasses and menus | `km/h` |
| **Feels-like temperature** | What customers actually feel (wind chill and heat index included) | `°C` |

All thresholds below are the values of the default `TEMPERATE` profile. Each one can be changed: see [Profiles and thresholds](profiles-and-thresholds.md).

### 1. Rain probability

Above `maxRainProbability` (default **40 %**), the hour loses a base of **25 points plus one point for every percentage point over the limit**, up to a maximum of 65.

`penalty = min(65, 25 + (probability − maxRainProbability))`

At 55 % that is `25 + 15 = 40` points. At 90 % it reaches the 65 cap. The 25-point base is intentional: once rain is *likely* enough to worry you, it should already move you out of "everything is fine".

### 2. Precipitation amount

- **At or above `maxPrecipitationMm` (default 0.5 mm): the terrace is closed outright** (hard stop, see below).
- Below that, every millimetre costs **20 points** (drizzle is unpleasant even when it is not enough to close).

Probability and amount are separate on purpose: "80 % chance of a shower that adds up to 0.1 mm" and "30 % chance of 3 mm" are very different problems.

### 3. Wind gusts

Above `maxGustsKmh` (default **35 km/h**), the hour loses **2 points per km/h over the limit**, up to 40. Gusts of **1.5 times the limit** (52.5 km/h by default) close the terrace outright.

`penalty = min(40, 2 × (gusts − maxGustsKmh))`

The API uses gusts rather than mean speed because a steady 15 km/h breeze is pleasant while gusts of 45 km/h are what actually cause accidents.

### 4. Feels-like temperature

Outside the comfort range (`minApparentTempC` 14 °C to `maxApparentTempC` 34 °C by default), the hour loses **4 points per degree** beyond the limit, up to 40.

`penalty = min(40, 4 × degrees outside the range)`

It uses *apparent* temperature, not air temperature: 30 °C with high humidity and no wind feels hotter than 34 °C in dry air.

## From points to a verdict

```
score = max(0, round(100 − total penalties))
```

| Score | Verdict | Suggested terrace capacity |
|---|---|---|
| 70–100 | `OPEN` | 100 % |
| 40–69 | `CAUTION` | 50 % |
| 0–39 | `CLOSED` | 0 % |

### Hard stops

Two conditions close the terrace **regardless of the points**, and cap the score at **20**:

1. Expected precipitation at or above `maxPrecipitationMm`.
2. Wind gusts at or above 1.5 × `maxGustsKmh`.

This prevents a false sense of safety: a mild afternoon with 80 km/h gusts must never show a score of 70 just because the other factors are perfect.

### Missing data

If the weather model does not provide rain probability for a location, that factor is skipped (measured precipitation still counts). If any other measurement is missing for an hour, **that hour is dropped** instead of being guessed. A gap in the data never reads as good weather.

## Worked examples

All examples use the default `TEMPERATE` profile unless noted. Each row is asserted by an automated test ([`DocumentedExamplesTest`](../src/test/java/com/terraceweather/scoring/DocumentedExamplesTest.java)), so this table cannot drift from the real behaviour.

| # | Situation | Feels like | Rain | Precip. | Gusts | Points lost | Score | Verdict |
|---|---|---|---|---|---|---|---|---|
| 1 | Perfect day | 24 °C | 5 % | 0 mm | 15 km/h | none | **100** | `OPEN` |
| 2 | Hot afternoon | 37 °C | 0 % | 0 mm | 20 km/h | temperature 12 | **88** | `OPEN` |
| 3 | Cloudy, rain likely | 22 °C | 55 % | 0 mm | 15 km/h | rain 40 | **60** | `CAUTION` |
| 4 | Light drizzle | 22 °C | 30 % | 0.3 mm | 15 km/h | precipitation 6 | **94** | `OPEN` |
| 5 | Strong gusts | 24 °C | 0 % | 0 mm | 50 km/h | wind 30 | **70** | `OPEN` |
| 6 | Extreme gusts | 24 °C | 0 % | 0 mm | 55 km/h | hard stop (wind) | **20** | `CLOSED` |
| 7 | Cold evening | 5 °C | 0 % | 0 mm | 10 km/h | temperature 36 | **64** | `CAUTION` |
| 8 | Measurable rain | 22 °C | 70 % | 1.0 mm | 15 km/h | hard stop (precipitation) | **20** | `CLOSED` |
| 9a | Bangkok afternoon, `TEMPERATE` | 33 °C | 60 % | 0.4 mm | 15 km/h | rain 45 + precipitation 8 | **47** | `CAUTION` |
| 9b | Same hour, `TROPICAL` | 33 °C | 60 % | 0.4 mm | 15 km/h | precipitation 8 | **92** | `OPEN` |
| 10 | Rain probability unavailable | 24 °C | unknown | 0 mm | 15 km/h | none | **100** | `OPEN` |

Rows 9a and 9b show why profiles exist: the same hour is a warning in a temperate city and perfectly normal in a tropical one.

Example 5 sits exactly on the `OPEN` boundary (70): one more km/h of gusts tips it into `CAUTION`. Scores near a boundary deserve the same caution as the verdict itself.

## From hours to windows

A service window (lunch, dinner, a reservation) is evaluated hour by hour and then summarised:

- **`verdict`**: the **worst** verdict of its hours. One `CLOSED` hour makes the window `CLOSED`.
- **`minScore`**: the lowest hourly score, which decides the verdict.
- **`averageScore`**: the mean, useful to tell "one bad hour" from "bad throughout".
- **`recommendedCapacityPercent`**: derived from the verdict (100 / 50 / 0).
- **`reasons`**: every distinct reason found in any hour of the window.

Why the worst hour and not the average? Because customers remember the rain, not the average. A 3-hour lunch with two perfect hours and one downpour is a `CLOSED` terrace for whoever is sitting there at 14:30.

## Choosing the best slot

`best-window` slides a window of `durationHours` across your service hours and ranks the candidates by:

1. highest `minScore` (the worst hour, as above),
2. then highest `averageScore`,
3. then the earliest start.

Alternatives are the next-best candidates that **do not overlap** the best one or each other, and a `CLOSED` slot is **never** offered as an alternative. If the whole day is closed, `best` still answers (with verdict `CLOSED`) and `alternatives` is empty: your cue to suggest the indoor room.

## Limits of the model

- It is a **decision aid, not a guarantee**: forecasts are probabilistic, and a `CLOSED` verdict or a wrong `OPEN` remains possible.
- It does not know your venue: shade, awnings, heaters, misting fans and windbreaks change what is comfortable. Encode that in your [thresholds](profiles-and-thresholds.md).
- It does not consider humidity separately, UV index or air quality (feels-like temperature partly reflects humidity).
- Forecast quality falls with distance: trust the next 24 hours more than day 3.
