# FAQ and glossary

## Frequently asked questions

### Do I need an API key or an account?
No. The API is open, with a rate limit of 60 requests per minute per IP address. See [Limits](limits-and-best-practices.md).

### Can I use it in a commercial product?
The code is MIT-licensed, so you can run and modify it freely. The hosted API relies on Open-Meteo's **free tier, which is for non-commercial use**; for a commercial product, run your own instance with an Open-Meteo commercial plan.

### Where does the weather data come from?
From [Open-Meteo.com](https://open-meteo.com/), which blends national weather models. This API adds the decision layer: scoring, verdicts, windows and recommendations.

### How far ahead can I look?
Three days (72 hours from the current hour). Beyond that there is no data and a `window` request returns `400`.

### How often does the data refresh?
Upstream data is cached for 15 minutes per ~1 km grid cell, so repeated calls within that time return identical values.

### Why is my terrace `CLOSED` when the weather looks fine?
Read the `reasons` field: it names the factor. The usual causes are (1) the profile does not fit your climate, for example the default `TEMPERATE` in a hot city (feels-like above 34 °C), (2) a hard stop from ≥ 0.5 mm precipitation or extreme gusts, or (3) a window that includes a single bad hour, because windows use the **worst** hour. See [How the score works](scoring.md).

### Why does the same forecast give different verdicts in different cities?
It is designed that way. 60 % rain is a warning in Madrid and normal in Bangkok; tolerance is a parameter (`profile` and thresholds), not a fixed truth. See [Profiles](profiles-and-thresholds.md).

### Can I have one covered and one open terrace?
Yes: call the API twice with different thresholds (for example a high `maxRainProbability` for the covered area) and use each verdict for its own tables.

### What time zone do I use?
The restaurant's own. All times you send and receive are local to the coordinates, and each response includes `timezone`.

### Why is `precipitationProbability` sometimes `null`?
Some weather models do not provide rain probability for every location. The API then skips that factor and relies on the expected precipitation amount. It never treats a missing value as "no rain" silently: unknown stays `null`.

### Why did one hour disappear from my forecast?
If a required measurement (temperature, wind, precipitation) is missing for an hour, the hour is dropped instead of being guessed. It is rare, and the alternative, a made-up value, would be worse.

### Does it work worldwide?
Yes, anywhere Open-Meteo has coverage, which is global. Forecast quality varies by region.

### Can I get notifications when the forecast changes?
Not directly: the API is stateless. Use a scheduled job as in [Recipe 6](integration-recipes.md#recipe-6-notify-a-customer-when-their-slot-gets-worse). Webhooks are on the roadmap.

### Is this a guarantee?
No. Weather forecasts are probabilistic. The API is a decision aid: use `CAUTION` as a prompt to prepare a fallback, and re-check on the day.

### I found a bug or want a feature.
Open an issue on the project's GitHub repository.

---

## Glossary

| Term | Meaning |
|---|---|
| **Apparent (feels-like) temperature** | What the air temperature feels like to a person once wind and humidity are taken into account. Used instead of the raw temperature because it reflects comfort. |
| **Gust** | A short burst of stronger wind. Gusts, not the average wind, topple parasols and glassware. |
| **Precipitation probability** | Chance (%) that measurable rain occurs during the hour. |
| **Precipitation amount** | Expected quantity of rain in the hour, in millimetres. |
| **Score** | 0–100 terrace viability for one hour. |
| **Verdict** | `OPEN`, `CAUTION` or `CLOSED`, derived from the score. |
| **Hard stop** | A condition (measurable rain, extreme gusts) that closes the terrace regardless of the other factors and caps the score at 20. |
| **Window** | A range of time (lunch, dinner, a reservation) evaluated as a whole by its worst hour. |
| **Profile** | A named set of thresholds for a type of climate: `TEMPERATE`, `TROPICAL`, `MEDITERRANEAN`, `NORDIC`. |
| **Threshold** | A tolerance limit (for example `maxRainProbability`) that you can override. |
| **Grid cell** | The ~1 km square around a coordinate (two decimals) that shares one cached forecast. |
| **Rate limit** | Maximum requests allowed per minute (60 per IP). |
