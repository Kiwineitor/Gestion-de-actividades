import { Client } from '@stomp/stompjs';
import { useAuthStore } from './stores/auth.js';
import { urlWebSocket } from './config.js';
import { registrarError } from './ui/registroErrores.js';

export function escucharTopic(topic, { onMensaje, onConectado, onCerrado } = {}) {
  const auth = useAuthStore();
  const client = new Client({
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    connectHeaders: { Authorization: `Bearer ${auth.token}` },
    onConnect: () => {
      client.subscribe(topic, (msg) => {
        try {
          onMensaje?.(JSON.parse(msg.body));
        } catch (_) {
        }
      });
      onConectado?.();
    },
    onWebSocketClose: () => onCerrado?.(),
    onWebSocketError: () => registrarError('No se pudo conectar al canal en tiempo real /ws'),
    onStompError: (frame) => {
      registrarError(frame.headers?.message || 'WebSocket rechazado');
      client.deactivate();
    },
  });
  client.activate();
  return () => client.deactivate();
}

export function crearClientePuestos(onEstado, onRechazo, onConectado, onNotificacion, onCerrado,
                                    onAsignaciones) {
  const auth = useAuthStore();

  const client = new Client({
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    connectHeaders: { Authorization: `Bearer ${auth.token}` },
    onConnect: () => {
      client.subscribe('/topic/puestos', (msg) => {
        try {
          onEstado(JSON.parse(msg.body));
        } catch (_) {
        }
      });
      if (onAsignaciones) {
        client.subscribe('/topic/asignaciones', (msg) => {
          try {
            onAsignaciones(JSON.parse(msg.body));
          } catch (_) {
          }
        });
      }
      if (auth.id != null && onNotificacion) {
        client.subscribe(`/topic/notificaciones/${auth.id}`, (msg) => {
          try {
            onNotificacion(JSON.parse(msg.body));
          } catch (_) {
          }
        });
      }
      if (onConectado) onConectado();
    },
    onWebSocketClose: () => {
      if (onCerrado) onCerrado();
    },
    onStompError: (frame) => {
      registrarError(frame.headers?.message || 'WebSocket rechazado');
      client.deactivate();
      if (onRechazo) onRechazo(frame.headers?.message || 'WebSocket rechazado');
    },
    onWebSocketError: () => registrarError('No se pudo conectar al canal en tiempo real /ws'),
  });
  client.activate();
  return client;
}

export function escucharTopicPublico(topic, { onMensaje, onConectado } = {}) {
  const client = new Client({
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    onConnect: () => {
      client.subscribe(topic, (msg) => {
        try {
          onMensaje?.(JSON.parse(msg.body));
        } catch (_) {
          return;
        }
      });
      onConectado?.();
    },
  });
  client.activate();
  return () => client.deactivate();
}
