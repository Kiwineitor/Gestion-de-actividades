const FUENTE = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';

export const COLOR_MESA = { L: '#1f9d63', P: '#2b8fd6', T: '#d98b0b', R: '#d98b0b', O: '#d23b5a', X: '#8a90a6' };
export const LEYENDA_MESAS = [
  { color: COLOR_MESA.L, texto: 'Libre' },
  { color: COLOR_MESA.R, texto: 'Reservada' },
  { color: COLOR_MESA.O, texto: 'Vendida' },
  { color: COLOR_MESA.X, texto: 'No disponible' },
];

async function cargarImagen(url) {
  if (!url) return null;
  try {
    const r = await fetch(url);
    if (!r.ok) return null;
    const blob = await r.blob();
    const local = URL.createObjectURL(blob);
    const img = new Image();
    img.src = local;
    await img.decode();
    return { img, liberar: () => URL.revokeObjectURL(local) };
  } catch {
    return null;
  }
}

function conAlfa(hex, alfa) {
  const h = String(hex || '#7c5cff').replace('#', '');
  const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h.slice(0, 6), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alfa})`;
}

function rectRedondeado(ctx, x, y, w, h, r) {
  const radio = Math.max(0, Math.min(r, w / 2, h / 2));
  ctx.beginPath();
  ctx.moveTo(x + radio, y);
  ctx.arcTo(x + w, y, x + w, y + h, radio);
  ctx.arcTo(x + w, y + h, x, y + h, radio);
  ctx.arcTo(x, y + h, x, y, radio);
  ctx.arcTo(x, y, x + w, y, radio);
  ctx.closePath();
}

function lineas(ctx, texto, ancho) {
  const palabras = String(texto || '').split(/\s+/).filter(Boolean);
  const res = [];
  let actual = '';
  for (const p of palabras) {
    const prueba = actual ? `${actual} ${p}` : p;
    if (ctx.measureText(prueba).width > ancho && actual) {
      res.push(actual);
      actual = p;
    } else {
      actual = prueba;
    }
  }
  if (actual) res.push(actual);
  return res;
}

function textoCentrado(ctx, texto, cx, cy, ancho, tam) {
  const filas = lineas(ctx, texto, ancho);
  const alto = tam * 1.1;
  filas.forEach((f, i) => ctx.fillText(f, cx, cy + (i - (filas.length - 1) / 2) * alto));
}

function lienzoBase(ancho, aspecto, cabecera, pie) {
  const altoPlano = Math.round(ancho * aspecto);
  const lienzo = document.createElement('canvas');
  lienzo.width = ancho;
  lienzo.height = altoPlano + cabecera + pie;
  const ctx = lienzo.getContext('2d');
  ctx.fillStyle = '#0b0d1a';
  ctx.fillRect(0, 0, lienzo.width, lienzo.height);
  return { lienzo, ctx, altoPlano };
}

function dibujarCabecera(ctx, ancho, alto, titulo, subtitulo) {
  const m = Math.round(ancho * 0.025);
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  ctx.fillStyle = '#ffffff';
  ctx.font = `800 ${Math.round(alto * 0.36)}px ${FUENTE}`;
  ctx.fillText(titulo || 'Plano', m, alto * 0.52);
  if (subtitulo) {
    ctx.fillStyle = '#b9b6e6';
    ctx.font = `600 ${Math.round(alto * 0.2)}px ${FUENTE}`;
    ctx.fillText(subtitulo, m, alto * 0.84);
  }
}

function dibujarLeyenda(ctx, ancho, y, alto, leyenda) {
  const m = Math.round(ancho * 0.025);
  const tam = Math.round(alto * 0.36);
  ctx.font = `600 ${tam}px ${FUENTE}`;
  ctx.textBaseline = 'middle';
  ctx.textAlign = 'left';
  let x = m;
  const cy = y + alto / 2;
  for (const l of leyenda) {
    ctx.fillStyle = l.color;
    ctx.beginPath();
    ctx.arc(x + tam / 2, cy, tam / 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#d7d4ff';
    ctx.fillText(l.texto, x + tam * 1.4, cy);
    x += tam * 1.4 + ctx.measureText(l.texto).width + tam * 1.6;
  }
}

async function dibujarFondo(ctx, ancho, alto, y0, fondoUrl, colorLiso) {
  ctx.save();
  ctx.translate(0, y0);
  ctx.fillStyle = colorLiso;
  ctx.fillRect(0, 0, ancho, alto);
  const fondo = await cargarImagen(fondoUrl);
  if (fondo) {
    ctx.drawImage(fondo.img, 0, 0, ancho, alto);
    fondo.liberar();
  } else {
    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.lineWidth = 1;
    const paso = ancho * 0.05;
    for (let x = paso; x < ancho; x += paso) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, alto); ctx.stroke(); }
    for (let y = paso; y < alto; y += paso) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(ancho, y); ctx.stroke(); }
  }
  ctx.restore();
}

function dibujarElementos(ctx, W, H, elementos) {
  for (const e of elementos || []) {
    const x = e.x * W;
    const y = e.y * H;
    const w = e.ancho * W;
    const h = e.alto * H;
    ctx.save();
    ctx.translate(x + w / 2, y + h / 2);
    if (e.rotacion) ctx.rotate((e.rotacion * Math.PI) / 180);
    if (e.tipo === 'zona') {
      rectRedondeado(ctx, -w / 2, -h / 2, w, h, Math.min(w, h) * 0.08);
      ctx.fillStyle = conAlfa(e.fondo, 0.22);
      ctx.fill();
      ctx.lineWidth = Math.max(2, W * 0.0015);
      ctx.strokeStyle = conAlfa(e.fondo, 0.8);
      ctx.stroke();
    }
    const tam = Math.max(8, (e.tamano || 0.025) * W);
    ctx.font = `800 ${tam}px ${FUENTE}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = e.color || (e.tipo === 'zona' ? '#e9e4ff' : '#ffffff');
    textoCentrado(ctx, String(e.texto || '').toUpperCase(), 0, 0, w * 0.94, tam);
    ctx.restore();
  }
}

function dibujarMesas(ctx, W, H, mesas) {
  for (const m of mesas || []) {
    if (m.mapaX == null || m.mapaY == null) continue;
    const w = (m.tamano || 0.06) * W;
    const h = m.forma === 'rectangulo' ? w / 1.7 : w;
    const cx = m.mapaX * W;
    const cy = m.mapaY * H;
    ctx.save();
    ctx.shadowColor = 'rgba(0,0,0,0.35)';
    ctx.shadowBlur = w * 0.12;
    ctx.fillStyle = COLOR_MESA[m.estado] || COLOR_MESA.L;
    if (m.forma === 'cuadrado' || m.forma === 'rectangulo') rectRedondeado(ctx, cx - w / 2, cy - h / 2, w, h, w * 0.13);
    else { ctx.beginPath(); ctx.arc(cx, cy, w / 2, 0, Math.PI * 2); }
    ctx.fill();
    ctx.shadowBlur = 0;
    ctx.lineWidth = Math.max(2, w * 0.06);
    ctx.strokeStyle = m.color || '#6d5bd0';
    ctx.stroke();
    ctx.fillStyle = '#ffffff';
    ctx.font = `700 ${Math.max(9, Math.min(h * 0.42, W * 0.02))}px ${FUENTE}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(String(m.codigo ?? ''), cx, cy);
    ctx.restore();
  }
}

function dibujarRotulos(ctx, W, H, textos) {
  for (const t of textos || []) {
    const cx = (t.mapaX ?? 0.5) * W;
    const cy = (t.mapaY ?? 0.5) * H;
    const tam = Math.max(6, (t.tamano ?? 0.02) * W);
    ctx.save();
    ctx.translate(cx, cy);
    if (t.rotacion) ctx.rotate((t.rotacion * Math.PI) / 180);
    if (t.fondo) {
      const w = (t.ancho ?? 0.16) * W;
      const h = (t.alto ?? 0.08) * H;
      rectRedondeado(ctx, -w / 2, -h / 2, w, h, Math.min(w, h) * 0.12);
      ctx.fillStyle = conAlfa(t.fondo, 0.28);
      ctx.fill();
      ctx.lineWidth = Math.max(2, tam * 0.15);
      ctx.strokeStyle = conAlfa(t.fondo, 0.9);
      ctx.stroke();
    }
    ctx.font = `800 ${tam}px ${FUENTE}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const grosor = (t.grosorBorde ?? 0.15) * tam * 2;
    if (grosor > 0 && !t.fondo) {
      ctx.lineJoin = 'round';
      ctx.lineWidth = grosor;
      ctx.strokeStyle = t.colorBorde || '#ffffff';
      ctx.strokeText(t.contenido || '', 0, 0);
    }
    ctx.fillStyle = t.color || '#111827';
    ctx.fillText(t.contenido || '', 0, 0);
    ctx.restore();
  }
}

function dibujarPuestos(ctx, W, H, puestos, colorDe) {
  for (const p of puestos || []) {
    if (p.mapaX == null || p.mapaY == null) continue;
    const w = (p.tamanoMapa ?? 0.012) * (p.mapaEscala ?? 1) * W;
    const h = w * (p.mapaProporcion ?? 1);
    ctx.save();
    ctx.translate(p.mapaX * W, p.mapaY * H);
    if (p.mapaRotacion) ctx.rotate((p.mapaRotacion * Math.PI) / 180);
    ctx.fillStyle = colorDe(p);
    if (p.forma === 'circulo') { ctx.beginPath(); ctx.ellipse(0, 0, w / 2, h / 2, 0, 0, Math.PI * 2); }
    else rectRedondeado(ctx, -w / 2, -h / 2, w, h, w * 0.1);
    ctx.fill();
    ctx.lineWidth = Math.max(1, w * 0.06);
    ctx.strokeStyle = 'rgba(255,255,255,0.8)';
    ctx.stroke();
    if (w > 14) {
      ctx.rotate(-((p.mapaRotacion || 0) * Math.PI) / 180);
      ctx.fillStyle = '#ffffff';
      ctx.font = `700 ${Math.max(7, Math.min(w, h) * 0.45)}px ${FUENTE}`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(p.codigo ?? ''), 0, 0);
    }
    ctx.restore();
  }
}

function aBlob(lienzo) {
  return new Promise((resolve, reject) => {
    try {
      lienzo.toBlob((b) => (b ? resolve(b) : reject(new Error('No se pudo generar la imagen'))), 'image/png');
    } catch (e) {
      reject(e);
    }
  });
}

export async function imagenPlanoMesas({ titulo, subtitulo, aspecto, fondoUrl, mesas, elementos, ancho = 1800 }) {
  const cab = Math.round(ancho * 0.07);
  const pie = Math.round(ancho * 0.04);
  const { lienzo, ctx, altoPlano } = lienzoBase(ancho, aspecto, cab, pie);
  dibujarCabecera(ctx, ancho, cab, titulo, subtitulo);
  await dibujarFondo(ctx, ancho, altoPlano, cab, fondoUrl, '#1b1f33');
  ctx.save();
  ctx.translate(0, cab);
  dibujarElementos(ctx, ancho, altoPlano, elementos);
  dibujarMesas(ctx, ancho, altoPlano, mesas);
  ctx.restore();
  dibujarLeyenda(ctx, ancho, cab + altoPlano, pie, LEYENDA_MESAS);
  return aBlob(lienzo);
}

export async function imagenPlanoFeria({ titulo, subtitulo, aspecto, fondoUrl, puestos, textos, leyenda = [], colorDe, ancho = 2000 }) {
  const cab = Math.round(ancho * 0.06);
  const pie = leyenda.length ? Math.round(ancho * 0.035) : Math.round(ancho * 0.01);
  const { lienzo, ctx, altoPlano } = lienzoBase(ancho, aspecto, cab, pie);
  dibujarCabecera(ctx, ancho, cab, titulo, subtitulo);
  await dibujarFondo(ctx, ancho, altoPlano, cab, fondoUrl, '#f4f5fb');
  ctx.save();
  ctx.translate(0, cab);
  dibujarRotulos(ctx, ancho, altoPlano, (textos || []).filter((t) => t.fondo));
  dibujarPuestos(ctx, ancho, altoPlano, puestos, colorDe || ((p) => p.color || '#94a3b8'));
  dibujarRotulos(ctx, ancho, altoPlano, (textos || []).filter((t) => !t.fondo));
  ctx.restore();
  if (leyenda.length) dibujarLeyenda(ctx, ancho, cab + altoPlano, pie, leyenda);
  return aBlob(lienzo);
}
