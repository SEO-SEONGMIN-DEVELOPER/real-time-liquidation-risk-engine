const StompClient = (() => {
  'use strict';

  const NULL = '\u0000';
  let ws = null;
  let connected = false;
  let subscriptions = {};
  let subIdCounter = 0;
  let onConnectCallback = null;
  let onDisconnectCallback = null;
  let reconnectTimer = null;
  let reconnectUrl = null;
  let reconnectAttempts = 0;
  const MAX_RECONNECT_DELAY = 30000;

  function buildFrame(command, headers, body) {
    let frame = command + '\n';
    for (const [key, val] of Object.entries(headers || {})) {
      frame += key + ':' + val + '\n';
    }
    frame += '\n';
    if (body) frame += body;
    frame += NULL;
    return frame;
  }

  function parseFrame(data) {
    const firstNull = data.indexOf(NULL);
    const content = firstNull >= 0 ? data.substring(0, firstNull) : data;

    const headerEnd = content.indexOf('\n\n');
    if (headerEnd < 0) return null;

    const headerPart = content.substring(0, headerEnd);
    const body = content.substring(headerEnd + 2);

    const lines = headerPart.split('\n');
    const command = lines[0];
    const headers = {};
    for (let i = 1; i < lines.length; i++) {
      const colonIdx = lines[i].indexOf(':');
      if (colonIdx > 0) {
        headers[lines[i].substring(0, colonIdx)] = lines[i].substring(colonIdx + 1);
      }
    }

    return { command, headers, body };
  }

  function connect(url, onConnect, onDisconnect) {
    reconnectUrl = url;
    onConnectCallback = onConnect;
    onDisconnectCallback = onDisconnect;
    doConnect(url);
  }

  function doConnect(url) {
    if (ws) {
      try { ws.close(); } catch (_) {}
    }

    ws = new WebSocket(url);

    ws.onopen = () => {
      ws.send(buildFrame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '0,0'
      }));
    };

    ws.onmessage = (evt) => {
      const data = typeof evt.data === 'string' ? evt.data : '';
      if (!data || data === '\n') return;

      const frame = parseFrame(data);
      if (!frame) return;

      switch (frame.command) {
        case 'CONNECTED':
          connected = true;
          reconnectAttempts = 0;
          console.log('[STOMP] Connected to', url);
          if (onConnectCallback) onConnectCallback();
          break;

        case 'MESSAGE': {
          const subId = frame.headers['subscription'];
          const handler = subscriptions[subId];
          if (handler) {
            try {
              const payload = frame.body ? JSON.parse(frame.body) : null;
              handler(payload, frame.headers);
            } catch (e) {
              console.error('[STOMP] Message parse error:', e);
            }
          }
          break;
        }

        case 'ERROR':
          console.error('[STOMP] Server error:', frame.body);
          break;
      }
    };

    ws.onclose = () => {
      const wasConnected = connected;
      connected = false;
      if (wasConnected && onDisconnectCallback) onDisconnectCallback();
      scheduleReconnect();
    };

    ws.onerror = (err) => {
      console.debug('[STOMP] WebSocket error:', err);
    };
  }

  function scheduleReconnect() {
    if (reconnectTimer) return;
    reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts - 1), MAX_RECONNECT_DELAY);
    console.log(`[STOMP] Reconnecting in ${delay}ms (attempt ${reconnectAttempts})`);
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      if (reconnectUrl) doConnect(reconnectUrl);
    }, delay);
  }

  function subscribe(destination, callback) {
    const id = 'sub-' + (subIdCounter++);
    subscriptions[id] = callback;

    if (connected && ws) {
      ws.send(buildFrame('SUBSCRIBE', {
        'id': id,
        'destination': destination
      }));
      console.log('[STOMP] Subscribed:', destination);
    }

    return {
      id,
      destination,
      unsubscribe() {
        if (connected && ws) {
          ws.send(buildFrame('UNSUBSCRIBE', { 'id': id }));
        }
        delete subscriptions[id];
      }
    };
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    reconnectUrl = null;
    if (connected && ws) {
      try { ws.send(buildFrame('DISCONNECT', {})); } catch (_) {}
    }
    if (ws) {
      try { ws.close(); } catch (_) {}
    }
    connected = false;
    subscriptions = {};
  }

  function isConnected() {
    return connected;
  }

  return { connect, subscribe, disconnect, isConnected };
})();
