import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs';

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/legacy/build/pdf.worker.min.mjs', import.meta.url).toString();

export async function primeraPaginaEnCanvas(datos, canvas, escala = 2) {
  const pdf = await pdfjsLib.getDocument({ data: datos, wasmUrl: `${import.meta.env.BASE_URL}pdfjs-wasm/` }).promise;
  const pagina = await pdf.getPage(1);
  const vista = pagina.getViewport({ scale: escala });
  canvas.width = vista.width;
  canvas.height = vista.height;
  await pagina.render({ canvasContext: canvas.getContext('2d'), viewport: vista }).promise;
  return canvas;
}
