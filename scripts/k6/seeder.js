import http from 'k6/http';

// ---- Env knobs --------------------------------------------------------------
const BASE_URL   = __ENV.BASE_URL || 'http://localhost:8080';
const SEED_N     = Number(__ENV.SEED_N || 1000);   // how many rows to create
const SEED_CHUNK = Number(__ENV.SEED_CHUNK || 100); // parallel POSTs per batch
const POST_URL   = `${BASE_URL}/orders`;
const ID_JSON_KEY = __ENV.ID_PATH || 'id';        // key in JSON response for id

export const options = {
  // Run exactly once (1 VU, 1 iteration) and finish
  scenarios: {
    seed: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '30m' },
  },
  discardResponseBodies: true,
  batchPerHost: Math.max(20, SEED_CHUNK * 2), // allow true parallel POSTs
  thresholds: {}, // no perf thresholds for seeding
};

// ---- helpers ----------------------------------------------------------------
function extractId(resp) {
  // Prefer JSON body
  try {
    const j = resp.json();
    if (j && j[ID_JSON_KEY] != null) return Number(j[ID_JSON_KEY]);
  } catch (_) { /* ignore */ }

  // Fallback: Location header like ".../orders/123"
  const loc = resp.headers && (resp.headers.Location || resp.headers.location);
  if (loc) {
    const m = String(loc).match(/\/(\d+)(?:\?.*)?$/);
    if (m) return Number(m[1]);
  }
  return undefined;
}

function seedOrders(total, chunk) {
  const ids = [];
  let remaining = total;

  while (remaining > 0) {
    const toMake = Math.min(remaining, chunk);
    const batch = [];
    for (let i = 0; i < toMake; i++) {
      const payload = JSON.stringify({
        customer: `seed-${Math.random().toString(36).slice(2, 8)}`,
        total_cents: Math.floor(Math.random() * 100000) + 100,
      });
      batch.push(['POST', POST_URL, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'POST /orders (seed)', type: 'orders_post' },
      }]);
    }
    const resps = http.batch(batch);
    for (const r of resps) {
      if (r.status === 201 || r.status === 200) {
        const id = extractId(r);
        if (Number.isFinite(id)) ids.push(id);
      }
    }
    remaining -= toMake;
  }
  return ids;
}

// ---- single-iteration body --------------------------------------------------
export default function () {
  const ids = seedOrders(SEED_N, SEED_CHUNK);
  if (ids.length) {
    const minId = ids.reduce((a, b) => Math.min(a, b), ids[0]);
    const maxId = ids.reduce((a, b) => Math.max(a, b), ids[0]);
    console.log(`Seeded ${ids.length}/${SEED_N} orders.`);
    console.log(`Use these bounds for your test: ID_MIN=${minId} ID_MAX=${maxId}`);
  } else {
    console.log('No IDs collected. Ensure your POST response includes an id (JSON or Location header).');
  }
}
