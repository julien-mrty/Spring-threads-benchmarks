import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';


// ---- Counter --------------------------------------------------------------
export const status_count       = new Counter('status_count');   // by exact code
export const status_family      = new Counter('status_family');  // 2xx/4xx/5xx
export const business_resps_ok  = new Rate('business_resps_ok');            // your definition of success


// ---- Env knobs --------------------------------------------------------------
const BASE_URL              = __ENV.BASE_URL || 'http://localhost:8080';
const RPS           = Number(__ENV.RPS || 2000); // desired requests/sec
const DURATION              = __ENV.DURATION || '1m';
const BATCH         = Number(__ENV.BATCH || 20);        // requests per iteration (parallel)
const AVG_ITER_MS   = Number(__ENV.AVG_ITER_MS || 130); // from your last runs (~131–132ms)
const ID_MIN        = Number(__ENV.ID_MIN ?? 1);
const ID_MAX        = Number(__ENV.ID_MAX ?? 1000);

if (!Number.isFinite(ID_MIN) || !Number.isFinite(ID_MAX) || ID_MIN > ID_MAX) {
  throw new Error(`Bad ID range: ID_MIN=${ID_MIN} ID_MAX=${ID_MAX}`);
}

// Optional: tune request mix (per "cycle" below)
const RATIO_GET  = Number(__ENV.RATIO_GET  || 4);
const RATIO_POST = Number(__ENV.RATIO_POST || 1);
const RATIO_SLOW = Number(__ENV.RATIO_SLOW || 1);

// ---- Endpoint paths (adjust if different in your app) -----------------------
const GET_ORDERS_URL  = `${BASE_URL}/orders/`;   // GET /orders/{id}
const POST_ORDERS_URL = `${BASE_URL}/orders`;    // POST /orders
const SLOW_URL        = `${BASE_URL}/orders/report/slow/300`;      // GET /slow (or change to your slow endpoint)

// ---- Derived sizing: VUs ≈ (RPS/BATCH) * avgIterSec -------------------------
const avgIterSec        = Math.max(0.001, AVG_ITER_MS / 1000);
const itersPerSec       = Math.max(1, Math.ceil(RPS / BATCH));           // integer iters/s for constant-arrival-rate
const requiredVUs       = Math.max(1, Math.ceil(itersPerSec * avgIterSec));
const preAllocatedVUs   = Math.max(requiredVUs, Number(__ENV.PRE_VUS || 0)); // allow manual floor
const maxVUs            = Math.ceil(preAllocatedVUs * 1.5);

// ---- k6 options -------------------------------------------------------------
export const options = {
  discardResponseBodies: true,
  batchPerHost: Math.max(20, BATCH * 2), // default is 6; raise to avoid per-host queuing
  thresholds: {
    'http_req_failed': ['rate<0.02'],
    'http_req_duration{type:orders_get}': ['p(95)<200'],
    'http_req_duration{type:orders_post}': ['p(95)<400'],
    'http_req_duration{type:slow}': ['p(95)<900'],

    // Force families to appear:
    'status_family{family:2xx}': ['count>=0'],
    'status_family{family:4xx}': ['count>=0'],
    'status_family{family:5xx}': ['count>=0'],

    // Force specific status codes to appear (add more if useful):
    // add per-type breakdown (this gives you which endpoint “type” returned 5xx):
    'status_count{status:500,type:orders_get}':  ['count>=0'],
    'status_count{status:500,type:orders_post}': ['count>=0'],
    'status_count{status:500,type:slow}':        ['count>=0'],
    'status_count{status:200,type:orders_get}':  ['count>=0'],
    'status_count{status:200,type:orders_post}': ['count>=0'],
    'status_count{status:200,type:slow}':        ['count>=0'],
    'status_count{status:201,type:orders_get}':  ['count>=0'],
    'status_count{status:201,type:orders_post}': ['count>=0'],
    'status_count{status:201,type:slow}':        ['count>=0'],
    'status_count{status:404,type:orders_get}':  ['count>=0'],
    'status_count{status:404,type:orders_post}': ['count>=0'],
    'status_count{status:404,type:slow}':        ['count>=0'],
  },
  scenarios: {
    mix: {
      executor: 'constant-arrival-rate',
      rate: itersPerSec,               // requests/sec
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs,
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

    const pickId = () => ID_MIN + Math.floor(Math.random() * (ID_MAX - ID_MIN + 1));

    // helper creators
    const mkGet = () => {
      return ['GET', `${GET_ORDERS_URL}${pickId()}`, null, {
        tags: { name: 'GET /orders/:id', type: 'orders_get' },
      }];
    };

    const mkPost = () => {
      const payload = JSON.stringify({ customer: 'alice', totalCents: 12345 });
      return ['POST', POST_ORDERS_URL, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'POST /orders', type: 'orders_post' },
      }];
    };

    const mkSlow = () => ['GET', SLOW_URL, null, {
      tags: { name: 'GET /orders/report/slow/300', type: 'slow' },
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
    const reqs = buildBatch();                 // keep a copy to know names
    const resps = http.batch(reqs);

    for (let i = 0; i < resps.length; i++) {
        const r = resps[i];
        const [method, url, _body, opts] = reqs[i];
        const st   = r.status | 0;
        const fam  = `${Math.floor(st / 100)}xx`;

        // business success (adjust if 202/204 are OK for you)
        const ok = (st === 200 || st === 201);
        business_resps_ok.add(ok);

        status_count.add(1,  { status: String(st), type: opts?.tags?.type });
        status_family.add(1, { family: fam, type: opts?.tags?.type });
    }
}

// ---- Summary --------------------------------------------------------------
export function handleSummary(data) {
    // Collect per-status totals from submetrics like: status_count{status:404, name:..., type:...}
    const rows = [];
    for (const [k, m] of Object.entries(data.metrics)) {
        if (!k.startsWith('status_count{')) continue;
        const tagStr = k.slice('status_count{'.length, -1); // inside {...}
        const tags = Object.fromEntries(tagStr.split(',').map(s => s.split(':').map(x => x.trim())));
        rows.push({ status: tags.status, type: tags.type, count: m.values.count });
    }

    // Aggregate by status code
    const byStatus = rows.reduce((acc, r) => (acc[r.status] = (acc[r.status] || 0) + r.count, acc), {});
    const header = '\nSTATUS BREAKDOWN (all requests)\nstatus\tcount';
    const lines  = Object.entries(byStatus)
    .sort((a,b) => Number(a[0]) - Number(b[0]))
    .map(([st, cnt]) => `${st}\t${cnt}`)
    .join('\n');

    // Top offenders by (type, status)
    const byName = [...rows].sort((a,b) => b.count - a.count).slice(0, 10)
    .map(r => `${r.status}\t${r.count}\t${r.type}`);

    const extra =
    `${header}\n${lines}\n\nTOP (status, count, type)\nstatus\tcount\ttype\n${byName.join('\n')}\n`;

    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }) + extra,
        'status_breakdown.json': JSON.stringify({ byStatus, rows }, null, 2),
    };
}
