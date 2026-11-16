/*
 * Service worker for family alerts (web push through Firebase Cloud Messaging).
 * It only shows pushes and opens the right screen when one is tapped; it does not cache the app.
 * Message format (from the backend's FcmPushSender): { notification: {title, body, icon}, fcmOptions: {link}, data }
 */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    payload = { notification: { title: event.data ? event.data.text() : 'Medicine Reminder' } };
  }
  const n = payload.notification || {};
  const link = (payload.fcmOptions && payload.fcmOptions.link) || (payload.data && payload.data.link) || '/';
  event.waitUntil(
    self.registration.showNotification(n.title || 'Medicine Reminder', {
      body: n.body || '',
      icon: n.icon || '/icon-192.png',
      tag: (payload.data && payload.data.doseLogId) || undefined,
      requireInteraction: true,
      data: { link },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const link = (event.notification.data && event.notification.data.link) || '/';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((wins) => {
      for (const w of wins) {
        if ('focus' in w) {
          w.navigate(link);
          return w.focus();
        }
      }
      return self.clients.openWindow(link);
    }),
  );
});
