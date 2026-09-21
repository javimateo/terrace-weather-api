// Propose the best time to a customer who wants the terrace, with fallbacks.
// Run:  node propose-time.mjs 2026-09-22 2
//       (date, duration in hours; both optional)
const BASE_URL = process.env.TERRACE_API_URL ?? 'https://terrace.javiermateo.dev';

const date = process.argv[2] ?? new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
const durationHours = process.argv[3] ?? '2';

const params = new URLSearchParams({
  lat: '13.7563', // Bangkok
  lon: '100.5018',
  date,
  durationHours,
  fromHour: '12', // service hours of the restaurant
  toHour: '23',
  profile: 'TROPICAL',
});

const response = await fetch(`${BASE_URL}/api/v1/terrace/best-window?${params}`);
if (!response.ok) {
  const problem = await response.json().catch(() => ({}));
  throw new Error(`API error ${response.status}: ${problem.detail ?? response.statusText}`);
}

const { best, alternatives } = await response.json();
const hhmm = (iso) => iso.slice(11, 16);
const slot = (w) => `${hhmm(w.start)}-${hhmm(w.end)}`;

console.log(`Best terrace slot on ${date}: ${slot(best)} (${best.verdict}, score ${best.minScore})`);

if (best.verdict === 'CLOSED') {
  console.log('No good outdoor slot today: suggest the indoor room.');
} else if (alternatives.length) {
  console.log('Other options: ' + alternatives.map((a) => `${slot(a)} (${a.verdict})`).join(', '));
}
