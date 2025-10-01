import React, { useEffect, useMemo, useState } from 'react'

// --- Grafana embed config (kept simple; same-origin via nginx proxy) ---
const GRAFANA_BASE_URL = '/grafana'
const GRAFANA_DASH_UID = 'yourDashUid'
const GRAFANA_DASH_SLUG = 'runner-dashboard'
const GRAFANA_ORG_ID = '1'

const LEAD_MS = 60_000
const LAG_MS = 60_000

// --- Types ---
type KPIs = { rps?: number; p95_ms?: number; errorRate?: number }
type RunSummary = {
    id: string
    rate: number
    durationSec: number
    startedAt: number | string
    status: string
    kpis?: KPIs
}

// --- Utils ---
function toEpochMs(x: number | string): number {
    if (typeof x === 'number') return x
    const t = Date.parse(x)
    return Number.isNaN(t) ? 0 : t
}

function buildGrafanaUrl(run: RunSummary): string {
    const startMs = toEpochMs(run.startedAt)
    const from = startMs - LEAD_MS
    const to = startMs + run.durationSec * 1000 + LAG_MS
    const params = new URLSearchParams({
        orgId: String(GRAFANA_ORG_ID),
        from: String(from),
        to: String(to),
        refresh: '5s',
        'var-runId': run.id,
    })
    const slug = GRAFANA_DASH_SLUG ? `/${GRAFANA_DASH_SLUG}` : ''
    return `${GRAFANA_BASE_URL}/d/${GRAFANA_DASH_UID}${slug}?${params.toString()}`
}

async function http<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetch(path, init)
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
    return res.json()
}

// --- Component ---
export default function RunnerDashboard() {
    // Core controls
    const [script, setScript] = useState<'constant_rate'>('constant_rate')
    const [rate, setRate] = useState(200)
    const [durationSec, setDurationSec] = useState(60)
    const [threadModel, setThreadModel] = useState('virtual')

    // Target + k6 env controls
    const [baseUrl, setBaseUrl] = useState('http://backend:8080')
    const [endpoint, setEndpoint] = useState('/api/test')
    const [promRWUrl, setPromRWUrl] = useState('http://prometheus:9090/api/v1/write')
    const [batch, setBatch] = useState(20)
    const [avgIterMs, setAvgIterMs] = useState(600)
    const [ratioGet, setRatioGet] = useState(4)
    const [ratioPost, setRatioPost] = useState(1)
    const [ratioSlow, setRatioSlow] = useState(1)

    // UI state
    const [showAdvanced, setShowAdvanced] = useState(false)
    const [runs, setRuns] = useState<RunSummary[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [embedRunId, setEmbedRunId] = useState<string | null>(null)

    // Derived
    const hasRunning = runs.some(r => r.status === 'RUNNING' || r.status === 'PENDING')
    const k6Duration = `${durationSec}s`
    const scriptPath = script === 'constant_rate' ? 'constant_rate.js' : 'constant_rate.js'

    // Poll runs
    useEffect(() => {
        let stopped = false
        const intervalMs = hasRunning ? 2000 : 8000
        async function tick() {
            try {
                const data = await http<RunSummary[]>('/runs')
                if (!stopped) setRuns(data)
            } catch (e: any) {
                if (!stopped) setError(e.message ?? String(e))
            }
        }
        tick()
        const id = setInterval(tick, intervalMs)
        return () => { stopped = true; clearInterval(id) }
    }, [hasRunning])

    async function startRun(e: React.FormEvent) {
        e.preventDefault()
        setLoading(true); setError(null)
        try {
            const body = {
                script: scriptPath,
                rate,
                durationSec,
                threadModel,
                // Everything configurable from UI goes here; runner forwards to k6
                env: {
                    K6_PROMETHEUS_RW_SERVER_URL: promRWUrl,
                    BASE_URL: baseUrl,
                    ENDPOINT: endpoint,
                    RPS: String(rate),
                    DURATION: k6Duration,
                    BATCH: String(batch),
                    AVG_ITER_MS: String(avgIterMs),
                    RATIO_GET: String(ratioGet),
                    RATIO_POST: String(ratioPost),
                    RATIO_SLOW: String(ratioSlow),
                },
            }


            const started = await http<{ runId: string; startedAt: number | string; durationSec: number }>(
                '/runs',
                { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
            )


            const newRun: RunSummary = {
                id: started.runId,
                rate,
                durationSec,
                startedAt: started.startedAt,
                status: 'PENDING',
            }
            setRuns(prev => [newRun, ...prev])
            setEmbedRunId(started.runId)
        } catch (e: any) {
            setError(e.message ?? String(e))
        } finally {
            setLoading(false)
        }
    }

    const embeddedRun = useMemo(() => runs.find(r => r.id === embedRunId) || null, [runs, embedRunId])
    const iframeUrl = embeddedRun ? buildGrafanaUrl(embeddedRun) : ''

    // Simple styles
    const box: React.CSSProperties = { background: '#fff', borderRadius: 12, padding: 16, boxShadow: '0 1px 6px rgba(0,0,0,0.08)', width: '100%'  }
    const input: React.CSSProperties = { padding: '8px 10px', border: '1px solid #cbd5e1', borderRadius: 8 }
    const btn: React.CSSProperties = { padding: '8px 12px', borderRadius: 10, border: '1px solid #0f172a', background: '#0f172a', color: '#fff' }

    return (
        <div style={{ minHeight: '100vh', background: '#f8fafc', color: '#0f172a' }}>
            <div
                style={{
                    width: 'min(1400px, 96vw)', // wide, but capped by viewport
                    margin: '0 auto',           // centered
                    padding: 'clamp(12px, 2vw, 24px)',
                }}
            >
                <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 16 }}>Runner Dashboard</h1>

                {/* --- Core form --- */}
                <form onSubmit={startRun} style={{ ...box, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, marginBottom: 16, justifyItems: 'stretch', alignItems: 'stretch' }}>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                        <span style={{ fontSize: 13 }}>Script</span>
                        <select value={script} onChange={e => setScript(e.target.value as any)} style={input}>
                            <option value="constant_rate">constant_rate</option>
                        </select>
                    </label>

                    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                        <span style={{ fontSize: 13 }}>Rate (RPS)</span>
                        <input type="number" min={1} step={1} required value={rate}
                               onChange={e => setRate(Number(e.target.value))} style={input} />
                    </label>

                    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                        <span style={{ fontSize: 13 }}>Duration (s)</span>
                        <input type="number" min={1} step={1} required value={durationSec}
                               onChange={e => setDurationSec(Number(e.target.value))} style={input} />
                    </label>

                    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                        <span style={{ fontSize: 13 }}>Thread model</span>
                        <select value={threadModel} onChange={e => setThreadModel(e.target.value as any)} style={input}>
                            <option value="virtual">virtual</option>
                            <option value="platform">platform</option>
                        </select>
                    </label>

                    <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 12, alignItems: 'center' }}>
                        <button type="button" style={{ ...btn, background: '#334155', borderColor: '#334155' }}
                                onClick={() => setShowAdvanced(v => !v)}>
                            {showAdvanced ? 'Hide advanced' : 'Show advanced'}
                        </button>
                        <button type="submit" disabled={loading} style={btn}>{loading ? 'Starting…' : 'Run'}</button>
                        {error && <span style={{ color: '#dc2626', fontSize: 13 }}>{error}</span>}
                    </div>
                </form>

                {/* --- Advanced params (collapsible) --- */}
                {showAdvanced && (
                    <section style={{ ...box, width:'100%', marginBottom: 16, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12, justifyItems: 'stretch', alignItems: 'stretch' }}>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>BASE_URL</span>
                            <input value={baseUrl} onChange={e => setBaseUrl(e.target.value)} style={input} />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>ENDPOINT</span>
                            <input value={endpoint} onChange={e => setEndpoint(e.target.value)} placeholder="/api/test" style={input} />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>K6_PROMETHEUS_RW_SERVER_URL</span>
                            <input value={promRWUrl} onChange={e => setPromRWUrl(e.target.value)} style={input} />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>BATCH</span>
                            <input type="number" min={1} step={1} value={batch} onChange={e => setBatch(Number(e.target.value))} style={input} />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>AVG_ITER_MS</span>
                            <input type="number" min={1} step={1} value={avgIterMs} onChange={e => setAvgIterMs(Number(e.target.value))} style={input} />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>RATIO_GET</span>
                            <input type="number" min={0} step={1} value={ratioGet} onChange={e => setRatioGet(Number(e.target.value))} style={input} />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>RATIO_POST</span>
                            <input type="number" min={0} step={1} value={ratioPost} onChange={e => setRatioPost(Number(e.target.value))} style={input} />
                        </label>
                        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width:'100%' }}>
                            <span style={{ fontSize: 13 }}>RATIO_SLOW</span>
                            <input type="number" min={0} step={1} value={ratioSlow} onChange={e => setRatioSlow(Number(e.target.value))} style={input} />
                        </label>
                    </section>
                )}

                {/* --- Embedded Grafana --- */}
                {embeddedRun && (
                    <section style={{ ...box, width:'100%' }}>
                        <div style={{ width:'100%', padding: 'clamp(12px, 2vw, 24px)',  display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                            <h2 style={{ fontWeight: 600 }}>Live charts for run {embeddedRun.id}</h2>
                            <a href={buildGrafanaUrl(embeddedRun)} target="_blank" rel="noreferrer" style={{ textDecoration: 'underline', fontSize: 13 }}>
                                Open in Grafana
                            </a>
                        </div>
                        <div style={{ width: '100%', height: '70vh', minHeight: 420, overflow: 'hidden', borderRadius: 10, border: '1px solid #e2e8f0' }}>
                            <iframe src={iframeUrl} style={{ width: '100%', height: '100%', border: 0 }} allow="fullscreen" />
                        </div>
                    </section>
                )}

                {/* --- Runs table --- */}
                <section style={{ ...box }}>
                    <h2 style={{ fontWeight: 600, marginBottom: 8 }}>Runs</h2>
                    <div style={{ width:'100%', overflowX: 'auto' }}>
                        <table style={{ width: '100%', fontSize: 14, borderCollapse: 'collapse' }}>
                            <thead>
                            <tr style={{ background: '#f1f5f9' }}>
                                <th style={{ textAlign: 'left', padding: 8 }}>Run ID</th>
                                <th style={{ textAlign: 'left', padding: 8 }}>Rate</th>
                                <th style={{ textAlign: 'left', padding: 8 }}>Duration</th>
                                <th style={{ textAlign: 'left', padding: 8 }}>Started</th>
                                <th style={{ textAlign: 'left', padding: 8 }}>Status</th>
                                <th style={{ textAlign: 'left', padding: 8 }}>KPIs</th>
                                <th style={{ textAlign: 'left', padding: 8 }}>Charts</th>
                            </tr>
                            </thead>
                            <tbody>
                            {runs.length === 0 && (
                                <tr><td colSpan={8} style={{ padding: 16, textAlign: 'center', color: '#64748b' }}>No runs yet</td></tr>
                            )}
                            {runs.map(run => {
                                const url = buildGrafanaUrl(run)
                                const started = new Date(toEpochMs(run.startedAt)).toLocaleString()
                                return (
                                    <tr key={run.id} style={{ borderTop: '1px solid #e2e8f0' }}>
                                        <td style={{ padding: 8, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12 }}>{run.id}</td>
                                        <td style={{ padding: 8 }}>{run.rate}</td>
                                        <td style={{ padding: 8 }}>{run.durationSec}s</td>
                                        <td style={{ padding: 8 }}>{started}</td>
                                        <td style={{ padding: 8 }}>{run.status}</td>
                                        <td style={{ padding: 8 }}>
                                            {run.kpis ? (
                                                <div style={{ display: 'flex', gap: 12, whiteSpace: 'nowrap' }}>
                                                    <span title="Requests per second">⚡ {run.kpis.rps ?? '—'}</span>
                                                    <span title="p95 http_req_duration">p95 {run.kpis.p95_ms ? `${run.kpis.p95_ms} ms` : '—'}</span>
                                                    <span title="Error rate">❗ {run.kpis.errorRate != null ? `${(run.kpis.errorRate * 100).toFixed(2)}%` : '—'}</span>
                                                </div>
                                            ) : '—'}
                                        </td>
                                        <td style={{ padding: 8 }}>
                                            <div style={{ display: 'flex', gap: 8 }}>
                                                <a href={url} target="_blank" rel="noreferrer" style={{ textDecoration: 'underline' }}>Open</a>
                                                <button onClick={() => setEmbedRunId(embedRunId === run.id ? null : run.id)}>
                                                    {embedRunId === run.id ? 'Hide' : 'Embed'}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                )
                            })}
                            </tbody>
                        </table>
                    </div>
                </section>

            </div>
        </div>
    )
}
