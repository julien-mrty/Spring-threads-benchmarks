export function envNum(name, def) {
  const raw = __ENV[name];
  // treat undefined, null, and empty string as “missing”
  if (raw === undefined || raw === null || raw === '') return def;
  const n = Number(raw);
  if (!Number.isFinite(n)) {
    throw new Error(`Env ${name} must be a finite number, got "${raw}"`);
  }
  return n;
}
