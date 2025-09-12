import http from 'k6/http';
import { check } from 'k6';

// ---- Env knobs --------------------------------------------------------------
const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8080';
const RATE         = Number(__ENV.RATE || 20000);      // target requests/sec
const DURATION     = __ENV.DURATION || '1m';
const BATCH        = Number(__ENV.BATCH || 20);        // requests per iteration (parallel)
const AVG_ITER_MS  = Number(__ENV.AVG_ITER_MS || 130); // from your last runs (~131–132ms)

// Optional: tune request mix (per "cycle" below)
const RATIO_GET  = Number(__ENV.RATIO_GET  || 4);
const RATIO_POST = Number(__ENV.RATIO_POST || 1);
const RATIO_SLOW = Number(__ENV.RATIO_SLOW || 1);

// ---- Endpoint paths (adjust if different in your app) -----------------------
const GET_ORDERS_URL  = `${BASE_URL}/orders/`;   // GET /orders/{id}
const POST_ORDERS_URL = `${BASE_URL}/orders`;    // POST /orders
const SLOW_URL        = `${BASE_URL}/slow`;      // GET /slow (or change to your slow endpoint)

// ---- Derived sizing: VUs ≈ (RPS/BATCH) * avgIterSec -------------------------
const avgIterSec     = AVG_ITER_MS / 1000;
const requiredVUs    = Math.max(1, Math.ceil((RATE / BATCH) * avgIterSec));
const preAllocatedVUs = Math.max(requiredVUs, Number(__ENV.PRE_VUS || 0)); // allow manual floor
const maxVUs          = Math.ceil(preAllocatedVUs * 1.5);

// ---- k6 options -------------------------------------------------------------
export const options = {
  discardResponseBodies: true,
  batchPerHost: Math.max(20, BATCH * 2), // default is 6; raise to avoid per-host queuing
  thresholds: {
    'http_req_failed': ['rate<0.02'],
    'http_req_duration{type:orders_get}': ['p(95)<200'],
    'http_req_duration{type:orders_post}': ['p(95)<400'],
    'http_req_duration{type:slow}': ['p(95)<900'],
  },
  scenarios: {
    mix: {
      executor: 'constant-arrival-rate',
      rate: RATE,               // requests/sec
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs,          // sized from RATE, BATCH, AVG_ITER_MS
      maxVUs,
      gracefulStop: '30s',
    },
  },
  systemTags: ['name','method','status','scenario'], // drop 'url', 'group', etc.
  summaryTrendStats: ['avg','p(95)','max'],          // fewer percentile calcs
};

// ---- Build one parallel batch of requests -----------------------------------
function buildBatch() {
  const cycle = [];

    // helper creators
    const mkGet = () => {
      const id = 1 + Math.floor(Math.random() * 100000);
      return ['GET', `${GET_ORDERS_URL}${id}`, null, {
        tags: { name: 'GET /orders/:id', type: 'orders_get' },
      }];
    };

    const mkPost = () => {
      const payload = JSON.stringify({ customer: 'alice', total_cents: 12345 });
      return ['POST', POST_ORDERS_URL, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'POST /orders', type: 'orders_post' },
      }];
    };

    const mkSlow = () => ['GET', SLOW_URL, null, {
      tags: { name: 'GET /slow', type: 'slow' },
    }];

  // compose a cycle like: G,G,G,G, P, S (default 4:1:1)
  for (let i = 0; i < RATIO_GET;  i++) cycle.push(mkGet());
  for (let i = 0; i < RATIO_POST; i++) cycle.push(mkPost());
  for (let i = 0; i < RATIO_SLOW; i++) cycle.push(mkSlow());

  // repeat the cycle until we reach BATCH length
  const reqs = [];
  while (reqs.length < BATCH) {
    for (const r of cycle) {
      if (reqs.length >= BATCH) break;
      reqs.push(r);
    }
  }
  return reqs;
}

// ---- VU code ----------------------------------------------------------------
export default function () {
  const responses = http.batch(buildBatch()); // all in parallel

  // minimal check to keep your "200 / created" threshold style
  for (const r of responses) {
    check(r, { '200/201': (res) => res.status === 200 || res.status === 201 });
  }
}
