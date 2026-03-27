(function () {
  const POLL_CONNECTED_MS = 10000;
  const POLL_DISCONNECTED_MS = 3500;
  const POLL_HIDDEN_MS = 15000;
  const RECONNECT_DELAY_MS = 2500;
  const CONNECTION_TIMEOUT_MS = 12000;
  const SEND_ACK_TIMEOUT_MS = 4000;

  function init() {
    const root = document.getElementById('privateChatRoot');
    if (!root || root.dataset.chatInit === '1') {
      return;
    }
    root.dataset.chatInit = '1';

    const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
      ? window.CaroUrl.path
      : function (value) { return value; };
    const ui = window.CaroUi || {};

    const currentUserId = String(root.dataset.currentUserId || window.CaroUser?.get?.()?.userId || '').trim();
    const friendId = String(root.dataset.friendId || '').trim();
    const currentUserName = String(root.dataset.currentUserName || window.CaroUser?.get?.()?.displayName || currentUserId || '').trim();
    const friendName = String(root.dataset.friendName || friendId || '').trim();
    const roomKey = String(root.dataset.roomKey || (
      currentUserId <= friendId ? (currentUserId + '__' + friendId) : (friendId + '__' + currentUserId)
    )).trim();

    const statusEl = document.getElementById('chatStatus');
    const chatBox = document.getElementById('privateChatBox');
    const input = document.getElementById('privateMessageInput');
    const sendBtn = document.getElementById('privateSendBtn');
    const initialHistory = Array.isArray(window.__privateChatHistory) ? window.__privateChatHistory : [];

    const seenMessageKeys = new Set();
    const pendingOutbound = new Map();
    let client = null;
    let connected = false;
    let historyLoading = false;
    let pollTimerId = 0;
    let reconnectTimerId = 0;
    let destroyed = false;

    function setStatus(message) {
      if (statusEl) {
        statusEl.textContent = String(message || '').trim();
      }
    }

    function showToast(message, type) {
      ui.toast?.(String(message || ''), { type: type || 'info' });
    }

    function parseJsonSafe(raw) {
      try {
        return JSON.parse(raw);
      } catch (_) {
        return null;
      }
    }

    function buildMessageKey(item) {
      if (!item || typeof item !== 'object') {
        return '';
      }
      if (item.messageId != null && item.messageId !== '') {
        return 'id:' + String(item.messageId);
      }
      if (item.clientMessageId != null && item.clientMessageId !== '') {
        return 'cid:' + String(item.clientMessageId);
      }
      return [
        'sig',
        String(item.fromUserId || ''),
        String(item.toUserId || ''),
        String(item.sentAt || ''),
        String(item.message || item.content || '')
      ].join('|');
    }

    function normalizeMessage(item) {
      if (!item || typeof item !== 'object') {
        return null;
      }
      return {
        messageId: item.messageId,
        clientMessageId: item.clientMessageId == null ? null : String(item.clientMessageId),
        type: String(item.type || 'PRIVATE_CHAT'),
        roomKey: String(item.roomKey || roomKey),
        fromUserId: String(item.fromUserId || ''),
        toUserId: String(item.toUserId || ''),
        userId: item.userId == null ? null : String(item.userId),
        senderName: String(item.senderName || item.fromUserId || friendName || 'Nguoi choi'),
        message: String(item.message || item.content || ''),
        sentAt: item.sentAt == null ? null : String(item.sentAt),
        error: item.error == null ? null : String(item.error)
      };
    }

    function createClientMessageId() {
      return [
        'pcm',
        Date.now().toString(36),
        Math.random().toString(36).slice(2, 10)
      ].join('-');
    }

    function resolvePendingOutbound(clientMessageId) {
      const normalized = String(clientMessageId || '').trim();
      if (!normalized) {
        return;
      }
      const pending = pendingOutbound.get(normalized);
      if (!pending) {
        return;
      }
      if (pending.timerId) {
        window.clearTimeout(pending.timerId);
      }
      pendingOutbound.delete(normalized);
      setStatus(pending.via === 'http' ? 'Da gui qua API' : 'Da gui qua realtime');
    }

    async function fallbackPendingRealtimeMessage(clientMessageId) {
      const pending = pendingOutbound.get(clientMessageId);
      if (!pending || pending.via !== 'realtime') {
        return;
      }
      pending.timerId = 0;
      pending.via = 'http';
      try {
        await sendViaHttp(pending.text, clientMessageId);
      } catch (error) {
        pendingOutbound.delete(clientMessageId);
        const message = String(error?.message || error || 'Khong gui duoc tin nhan');
        setStatus(message);
        showToast(message, 'danger');
      }
    }

    function trackRealtimePendingMessage(clientMessageId, text) {
      const normalized = String(clientMessageId || '').trim();
      if (!normalized) {
        return;
      }
      const pending = {
        text: String(text || ''),
        via: 'realtime',
        timerId: 0
      };
      pending.timerId = window.setTimeout(() => {
        void fallbackPendingRealtimeMessage(normalized);
      }, SEND_ACK_TIMEOUT_MS);
      pendingOutbound.set(normalized, pending);
    }

    function appendMessage(payload) {
      const message = normalizeMessage(payload);
      if (!message || !message.message) {
        return;
      }
      if (message.clientMessageId && message.fromUserId === currentUserId) {
        resolvePendingOutbound(message.clientMessageId);
      }
      const key = buildMessageKey(message);
      if (key && seenMessageKeys.has(key)) {
        return;
      }
      if (key) {
        seenMessageKeys.add(key);
      }

      const isMine = message.fromUserId === currentUserId;
      const row = document.createElement('div');
      row.className = 'private-chat-message-row ' + (isMine ? 'private-chat-message-row--mine' : 'private-chat-message-row--other');

      const bubble = document.createElement('div');
      bubble.className = 'private-chat-message ' + (isMine ? 'private-chat-message--mine' : 'private-chat-message--other');

      const senderEl = document.createElement('div');
      senderEl.className = 'private-chat-message__sender';
      senderEl.textContent = message.senderName || (isMine ? currentUserName : friendName);
      bubble.appendChild(senderEl);

      const textEl = document.createElement('div');
      textEl.className = 'private-chat-message__body';
      textEl.textContent = message.message;
      bubble.appendChild(textEl);

      if (message.sentAt) {
        const timeEl = document.createElement('div');
        timeEl.className = 'private-chat-message__meta';
        try {
          timeEl.textContent = new Date(message.sentAt).toLocaleTimeString();
        } catch (_) {
          timeEl.textContent = message.sentAt;
        }
        bubble.appendChild(timeEl);
      }

      row.appendChild(bubble);
      chatBox.appendChild(row);
      chatBox.scrollTop = chatBox.scrollHeight;
    }

    function mergeHistory(messages) {
      if (!Array.isArray(messages)) {
        return;
      }
      messages.forEach((item) => {
        appendMessage(item);
      });
    }

    function handleIncoming(frameBody) {
      const payload = normalizeMessage(frameBody);
      if (!payload) {
        return;
      }
      if (payload.type === 'ERROR') {
        if (!payload.roomKey || payload.roomKey === roomKey || payload.userId === currentUserId) {
          setStatus(payload.error || 'Khong gui duoc tin nhan');
          if (payload.userId === currentUserId) {
            showToast(payload.error || 'Khong gui duoc tin nhan', 'danger');
          }
        }
        return;
      }
      if (payload.type !== 'PRIVATE_CHAT') {
        return;
      }
      if (payload.roomKey && payload.roomKey !== roomKey) {
        return;
      }
      appendMessage(payload);
    }

    async function loadHistory(silent) {
      if (historyLoading || destroyed || !friendId) {
        return;
      }
      historyLoading = true;
      try {
        const response = await fetch(appPath('/chat/private/api?friendId=' + encodeURIComponent(friendId)), {
          cache: 'no-store'
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok || data.success === false) {
          const message = String(data?.error || 'Khong the dong bo lich su chat');
          if (/login required/i.test(message)) {
            window.location.href = appPath('/account/login-page');
            return;
          }
          throw new Error(message);
        }
        mergeHistory(data.messages);
        if (!connected) {
          setStatus('Dang dong bo qua API');
        }
      } catch (error) {
        if (!silent) {
          setStatus(String(error?.message || error || 'Khong the dong bo lich su chat'));
        }
      } finally {
        historyLoading = false;
      }
    }

    function currentPollIntervalMs() {
      if (document.hidden) {
        return POLL_HIDDEN_MS;
      }
      return connected ? POLL_CONNECTED_MS : POLL_DISCONNECTED_MS;
    }

    function schedulePoll() {
      if (destroyed) {
        return;
      }
      if (pollTimerId) {
        window.clearTimeout(pollTimerId);
      }
      pollTimerId = window.setTimeout(async () => {
        await loadHistory(true);
        schedulePoll();
      }, currentPollIntervalMs());
    }

    function clearReconnectTimer() {
      if (reconnectTimerId) {
        window.clearTimeout(reconnectTimerId);
        reconnectTimerId = 0;
      }
    }

    function scheduleReconnect() {
      if (destroyed || reconnectTimerId || navigator.onLine === false) {
        return;
      }
      reconnectTimerId = window.setTimeout(() => {
        reconnectTimerId = 0;
        connectRealtime();
      }, RECONNECT_DELAY_MS);
    }

    function handleRealtimeLoss(message) {
      connected = false;
      setStatus(message || 'Mat ket noi realtime - dang chuyen qua API');
      schedulePoll();
      scheduleReconnect();
    }

    function connectRealtime() {
      if (destroyed || !friendId || !roomKey) {
        return;
      }
      clearReconnectTimer();

      if (!window.StompJs || typeof window.SockJS === 'undefined') {
        connected = false;
        setStatus('Realtime khong san sang - dang dong bo qua API');
        schedulePoll();
        return;
      }

      try {
        if (client) {
          client.deactivate();
        }
      } catch (_) {
      }

      client = new window.StompJs.Client({
        webSocketFactory: () => new window.SockJS(appPath('/ws'), null, {
          transports: ['websocket', 'xhr-streaming', 'xhr-polling']
        }),
        reconnectDelay: 0,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        connectionTimeout: CONNECTION_TIMEOUT_MS,
        onConnect: () => {
          connected = true;
          setStatus('Da ket noi realtime');
          schedulePoll();
          client.subscribe('/topic/private.' + roomKey, (messageFrame) => {
            handleIncoming(parseJsonSafe(messageFrame.body));
          });
          client.subscribe('/user/queue/private-chat', (messageFrame) => {
            handleIncoming(parseJsonSafe(messageFrame.body));
          });
          void loadHistory(true);
        },
        onStompError: () => {
          handleRealtimeLoss('Loi STOMP - dang chuyen qua API');
        },
        onWebSocketClose: () => {
          handleRealtimeLoss('Mat ket noi - dang thu lai...');
        },
        onWebSocketError: () => {
          handleRealtimeLoss('Loi WebSocket - dang dong bo qua API');
        }
      });

      try {
        client.activate();
        setStatus('Dang ket noi realtime...');
      } catch (_) {
        handleRealtimeLoss('Khong the khoi tao realtime');
      }
    }

    async function sendViaHttp(text, clientMessageId) {
      const response = await fetch(appPath('/chat/private/api/send'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          friendId: friendId,
          content: text,
          clientMessageId: clientMessageId
        })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || data.success === false) {
        throw new Error(String(data?.error || 'Khong gui duoc tin nhan'));
      }
      handleIncoming(data.payload);
      setStatus('Da gui qua API');
      schedulePoll();
    }

    async function sendMessage() {
      const text = String(input?.value || '').trim();
      if (!text) {
        return;
      }
      const previousValue = input.value;
      input.value = '';
      input.focus();
      const clientMessageId = createClientMessageId();

      if (connected && client && client.connected) {
        try {
          trackRealtimePendingMessage(clientMessageId, text);
          client.publish({
            destination: '/app/private-chat.send',
            body: JSON.stringify({
              friendId: friendId,
              content: text,
              clientMessageId: clientMessageId
            })
          });
          setStatus('Dang gui qua realtime...');
          return;
        } catch (_) {
          resolvePendingOutbound(clientMessageId);
          connected = false;
          scheduleReconnect();
        }
      }

      try {
        pendingOutbound.set(clientMessageId, { text: text, via: 'http', timerId: 0 });
        await sendViaHttp(text, clientMessageId);
      } catch (error) {
        pendingOutbound.delete(clientMessageId);
        input.value = previousValue;
        setStatus(String(error?.message || error || 'Khong gui duoc tin nhan'));
        showToast(String(error?.message || error || 'Khong gui duoc tin nhan'), 'danger');
      }
    }

    function cleanup() {
      destroyed = true;
      if (pollTimerId) {
        window.clearTimeout(pollTimerId);
        pollTimerId = 0;
      }
      pendingOutbound.forEach((pending) => {
        if (pending && pending.timerId) {
          window.clearTimeout(pending.timerId);
        }
      });
      pendingOutbound.clear();
      clearReconnectTimer();
      try {
        client?.deactivate();
      } catch (_) {
      }
      client = null;
      connected = false;
    }

    mergeHistory(initialHistory);
    schedulePoll();
    connectRealtime();

    sendBtn?.addEventListener('click', () => {
      void sendMessage();
    });

    input?.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        void sendMessage();
      }
    });

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) {
        void loadHistory(true);
        if (!connected) {
          connectRealtime();
        }
      }
      schedulePoll();
    });

    window.addEventListener('focus', () => {
      void loadHistory(true);
      if (!connected) {
        connectRealtime();
      }
    });

    window.addEventListener('online', () => {
      void loadHistory(true);
      connectRealtime();
    });

    window.addEventListener('offline', () => {
      handleRealtimeLoss('Mat mang - tam chuyen sang dong bo API');
    });

    window.addEventListener('beforeunload', cleanup, { once: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
