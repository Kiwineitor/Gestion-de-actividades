const ACTIVO = Boolean(import.meta.env?.DEV);

const enCurso = new Map();

export function iniciarMedicion(id, etiqueta, evento) {
  if (!ACTIVO) return;
  const ahora = performance.now();
  const entrada = evento?.timeStamp ? ahora - evento.timeStamp : null;
  enCurso.set(id, { t0: ahora, etiqueta, entrada, pintado: null, red: null });
}

export function marcarPintado(id) {
  const m = ACTIVO && enCurso.get(id);
  if (m) m.pintado = performance.now() - m.t0;
}

export function marcarRed(id) {
  const m = ACTIVO && enCurso.get(id);
  if (m) m.red = performance.now() - m.t0;
}

export function cerrarMedicion(id) {
  const m = ACTIVO && enCurso.get(id);
  if (!m) return;
  enCurso.delete(id);
  const total = performance.now() - m.t0;
  const n = (v) => (v == null ? '    —' : `${Math.round(v)}`.padStart(5) + 'ms');
  const lento = total > 300 || (m.entrada ?? 0) > 150 ? '   <<< LENTO' : '';
  console.log(
    `[medir] ${m.etiqueta} caseta ${id} · entrada ${n(m.entrada)}`
    + ` · pintado ${n(m.pintado)} · red ${n(m.red)} · broadcast ${n(total)}${lento}`,
  );
}

export function purgarMediciones(msLimite = 6000) {
  if (!ACTIVO) return;
  const ahora = performance.now();
  for (const [id, m] of enCurso) {
    if (ahora - m.t0 > msLimite) {
      enCurso.delete(id);
      console.warn(`[medir] ${m.etiqueta} caseta ${id} · EL BROADCAST NUNCA LLEGO (>${msLimite} ms)`);
    }
  }
}
