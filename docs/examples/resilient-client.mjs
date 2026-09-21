// A small fetch wrapper with the retry policy recommended in docs/limits-and-best-practices.md:
//  - 429 (rate limit): wait for the Retry-After header, then retry
//  - 502 (weather provider down): exponential backoff, a few attempts
//  - 400 (bad request): never retry, surface the message
const BASE_URL = process.env.TERRACE_API_URL ?? 'https://terrace.javiermateo.dev';

export async function terraceRequest(path, options = {}, maxAttempts = 4) {
  for (let attempt = 1; ; attempt++) {
    const response = await fetch(`${BASE_URL}${path}`, options);

    if (response.ok) return response.json();

    const problem = await response.json().catch(() => ({}));
    const detail = problem.detail ?? response.statusText;

    if (response.status === 429 && attempt < maxAttempts) {
      const wait = Number(response.headers.get('Retry-After') ?? 1);
      await new Promise((r) => setTimeout(r, wait * 1000));
      continue;
    }
    if (response.status === 502 && attempt < maxAttempts) {
      await new Promise((r) => setTimeout(r, 500 * 2 ** attempt));
      continue;
    }
    throw new Error(`Terrace API ${response.status}: ${detail}`);
  }
}

// Demo: node resilient-client.mjs
if (import.meta.url === `file://${process.argv[1].replace(/\\/g, '/')}` ||
    process.argv[1]?.endsWith('resilient-client.mjs')) {
  const data = await terraceRequest('/api/v1/terrace/forecast?lat=40.4168&lon=-3.7038&hours=3');
  for (const h of data.hours) {
    console.log(`${h.conditions.time.slice(11, 16)}  ${h.verdict.padEnd(7)} score ${h.score}`);
  }
}
