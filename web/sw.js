// Service Worker cho Ứng dụng Nhà Thông Minh ESP32
// CHỈ cache các tài nguyên tĩnh nội bộ; TUYỆT ĐỐI KHÔNG cache Firebase, Google APIs hay Realtime Database

const CACHE_NAME = "smart-home-static-v2";
const STATIC_ASSETS = [
  "./",
  "./index.html",
  "./styles.css",
  "./chart.umd.min.js",
  "./manifest.webmanifest"
];

self.addEventListener("install", event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      return cache.addAll(STATIC_ASSETS);
    })
  );
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys().then(keys => {
      return Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", event => {
  const url = new URL(event.request.url);

  // BỎ QUA HOÀN TOÀN: Tuyệt đối không can thiệp hoặc cache bất kỳ request nào liên quan tới Firebase / Realtime Database / Google Auth
  if (
    url.hostname.includes("firebaseio.com") ||
    url.hostname.includes("firebasedatabase.app") ||
    url.hostname.includes("googleapis.com") ||
    url.hostname.includes("gstatic.com") ||
    url.hostname.includes("google.com") ||
    url.protocol.startsWith("ws") ||
    event.request.method !== "GET"
  ) {
    return;
  }

  // Chiến lược Network-First cho HTML/CSS và điều hướng để luôn nhận giao diện mới nhất ngay khi có thay đổi
  if (
    event.request.mode === "navigate" ||
    url.pathname.endsWith(".html") ||
    url.pathname.endsWith(".css") ||
    url.pathname === "/" ||
    url.pathname.endsWith("/web/")
  ) {
    event.respondWith(
      fetch(event.request)
        .then(response => {
          if (response && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
          }
          return response;
        })
        .catch(() => caches.match(event.request).then(cached => cached || caches.match("./index.html")))
    );
    return;
  }

  // Đối với các tài nguyên tĩnh khác: Trả về cache nếu có, đồng thời lấy qua mạng nếu chưa có
  event.respondWith(
    caches.match(event.request).then(cachedResponse => {
      if (cachedResponse) {
        return cachedResponse;
      }
      return fetch(event.request).catch(() => {
        // Fallback khi offline
        return caches.match("./index.html");
      });
    })
  );
});
