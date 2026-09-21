// Morning check: decide, for every terrace reservation of a day, whether to keep the terrace table.
// Run:  node morning-check.mjs            (uses TERRACE_API_URL, default: the public API)
//       TERRACE_API_URL=http://localhost:8080 node morning-check.mjs
const BASE_URL = process.env.TERRACE_API_URL ?? 'https://terrace.javiermateo.dev';

const restaurant = { lat: 40.4168, lon: -3.7038, profile: 'MEDITERRANEAN' }; // Madrid

// In a real system these come from your reservation software.
const day = process.argv[2] ?? new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
const reservations = [
  { id: 'R-1041 (Garcia, 4 pax)', time: '13:30', durationH: 2 },
  { id: 'R-1042 (Lopez, 2 pax)', time: '14:00', durationH: 1.5 },
  { id: 'R-1050 (Martin, 6 pax)', time: '21:00', durationH: 2 },
];

// Reservations -> one batch request (max 25 windows, counts as a single call for the rate limit).
const addHours = (date, time, hours) => {
  const start = new Date(`${date}T${time}:00Z`);
  const end = new Date(start.getTime() + hours * 3_600_000);
  return [start, end].map((d) => d.toISOString().slice(0, 19)); // "2026-09-22T13:30:00"
};

const windows = reservations.map((r) => {
  const [start, end] = addHours(day, r.time, r.durationH);
  return { id: r.id, start, end };
});

const response = await fetch(`${BASE_URL}/api/v1/terrace/windows`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    lat: restaurant.lat,
    lon: restaurant.lon,
    rules: { profile: restaurant.profile },
    windows,
  }),
});

if (!response.ok) {
  const problem = await response.json().catch(() => ({}));
  throw new Error(`API error ${response.status}: ${problem.detail ?? response.statusText}`);
}

const { results } = await response.json();

const advice = {
  OPEN: 'Keep the terrace table',
  CAUTION: 'Offer indoor as backup',
  CLOSED: 'Move to indoor',
};

for (const r of results) {
  if (r.error) {
    console.log(`${r.id}: could not be checked (${r.error})`);
    continue;
  }
  const w = r.window;
  const why = w.reasons.length ? ` - ${w.reasons.join('; ')}` : '';
  console.log(`${r.id}: ${w.verdict} (score ${w.minScore}) -> ${advice[w.verdict]}${why}`);
}
