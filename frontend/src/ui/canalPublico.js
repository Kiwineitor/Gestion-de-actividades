import { Client } from '@stomp/stompjs';
import { urlWebSocket } from '../config.js';

export const TOPIC_CAMBIOS = '/topic/publico/cambios';

let cliente = null;
const topics = new Map();
const alConectar = new Set();

function suscribir(topic) {
  return cliente.subscribe(topic, (msg) => {
    let datos;
    try {
      datos = JSON.parse(msg.body);
    } catch (_) {
      return;
    }
    topics.get(topic)?.oyentes.forEach((f) => f(datos));
  });
}

function asegurar() {
  if (cliente) return;
  cliente = new Client({
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    onConnect: () => {
      for (const [topic, e] of topics) e.sub = suscribir(topic);
      alConectar.forEach((f) => f());
    },
  });
  cliente.activate();
}

export function escucharPublico(topic, oyente, { reconectado } = {}) {
  asegurar();
  let e = topics.get(topic);
  if (!e) {
    e = { oyentes: new Set(), sub: null };
    topics.set(topic, e);
    if (cliente.connected) e.sub = suscribir(topic);
  }
  e.oyentes.add(oyente);
  if (reconectado) alConectar.add(reconectado);
  return () => {
    e.oyentes.delete(oyente);
    if (reconectado) alConectar.delete(reconectado);
    if (!e.oyentes.size) {
      try {
        e.sub?.unsubscribe();
      } catch (_) {
        e.sub = null;
      }
      topics.delete(topic);
    }
    if (!topics.size && cliente) {
      cliente.deactivate();
      cliente = null;
    }
  };
}

export function alCambiarPublico(oyente, opciones) {
  return escucharPublico(TOPIC_CAMBIOS, oyente, opciones);
}
