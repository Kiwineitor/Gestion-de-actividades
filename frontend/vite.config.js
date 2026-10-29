import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const PDF_WASM = ['jbig2.wasm', 'jbig2_nowasm_fallback.js', 'openjpeg.wasm', 'openjpeg_nowasm_fallback.js', 'qcms_bg.wasm'];
const origenWasm = (nombre) => fileURLToPath(new URL(`./node_modules/pdfjs-dist/wasm/${nombre}`, import.meta.url));

function pdfWasm() {
  return {
    name: 'pdfjs-wasm',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const nombre = req.url?.split('?')[0].match(/\/pdfjs-wasm\/([^/]+)$/)?.[1];
        if (!nombre || !PDF_WASM.includes(nombre)) return next();
        res.setHeader('Content-Type', nombre.endsWith('.wasm') ? 'application/wasm' : 'text/javascript');
        res.end(readFileSync(origenWasm(nombre)));
      });
    },
    generateBundle() {
      for (const nombre of PDF_WASM) {
        this.emitFile({ type: 'asset', fileName: `pdfjs-wasm/${nombre}`, source: readFileSync(origenWasm(nombre)) });
      }
    },
  };
}

const BACKEND = process.env.VITE_BACKEND || 'http://127.0.0.1:7676';

export default defineConfig({
  plugins: [vue(), pdfWasm()],
  base: process.env.VITE_BASE || '/',
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true, xfwd: true },
      '/ws': { target: BACKEND, changeOrigin: true, ws: true, xfwd: true },
      '/files': { target: BACKEND, changeOrigin: true, xfwd: true },
    },
  },
});
