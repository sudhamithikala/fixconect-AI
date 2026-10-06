/* ==========================================================================
   FIXCONNECT AI - LIVE TECHNICIAN TRACKING (Swiggy / Zomato style)
   - Real street map (Leaflet + OpenStreetMap tiles, no API key needed)
   - Technician marker glides to each new GPS position
   - Road route + road ETA from the free OSRM routing service
   - Status stepper, "Arriving in N mins", last-updated counter
   Data comes from GET /api/customer/requests/{id}/tracking (polled every 5 s).
   The technician's phone/browser pushes its GPS via PUT /api/provider/location
   (see provider-dashboard.js -> LiveShare).
   Requires api.js (window.FixConnect) and leaflet.js.
   ========================================================================== */
(function () {
  const FC = window.FixConnect;
  const POLL_MS = 5000;                 // how often we ask the server for the latest position
  const ROUTE_EVERY_MS = 30000;         // re-calculate the road route at most every 30 s ...
  const ROUTE_MOVE_M = 150;             // ... or when the technician moved more than 150 m
  const STALE_AFTER_S = 120;            // location older than this = "signal weak"
  // Standard OpenStreetMap map: free, no API key, shows real street / area names
  const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
  const TILE_CREDIT = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors';
  const OSRM_URL = 'https://router.project-osrm.org/route/v1/driving/';
  // Address lookup limited to India, so a misspelled address can never land in another country
  const GEOCODE_URL = 'https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=in&q=';
  const inIndia = (lat, lng) => lat != null && lng != null && lat >= 6 && lat <= 37.5 && lng >= 68 && lng <= 97.5;

  const STEPS = [
    { key: 'ACCEPTED', label: 'Assigned', icon: 'fa-user-check' },
    { key: 'EN_ROUTE', label: 'On the way', icon: 'fa-motorcycle' },
    { key: 'ARRIVED', label: 'Arrived', icon: 'fa-location-dot' },
    { key: 'IN_PROGRESS', label: 'Repairing', icon: 'fa-screwdriver-wrench' },
    { key: 'COMPLETED', label: 'Done', icon: 'fa-circle-check' },
  ];

  const S = {
    requestId: null, map: null, tech: null, home: null, route: null, line: null,
    techPos: null, homePos: null, anim: null, timer: null, tick: null,
    lastRouteAt: 0, lastRoutePos: null, roadEta: null, roadKm: null,
    updatedAt: null, userMoved: false, fitted: false, data: null,
  };

  // ------------------------------------------------------------ helpers
  const esc = FC.esc;
  const meters = (a, b) => {
    if (!a || !b) return Infinity;
    const R = 6371000, toR = Math.PI / 180;
    const dLat = (b[0] - a[0]) * toR, dLng = (b[1] - a[1]) * toR;
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * toR) * Math.cos(b[0] * toR) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
  };
  const bearing = (a, b) => {
    const toR = Math.PI / 180;
    const y = Math.sin((b[1] - a[1]) * toR) * Math.cos(b[0] * toR);
    const x = Math.cos(a[0] * toR) * Math.sin(b[0] * toR) - Math.sin(a[0] * toR) * Math.cos(b[0] * toR) * Math.cos((b[1] - a[1]) * toR);
    return (Math.atan2(y, x) * 180) / Math.PI;
  };
  const ago = (date) => {
    if (!date) return '';
    const s = Math.max(0, Math.round((Date.now() - date.getTime()) / 1000));
    if (s < 10) return 'just now';
    if (s < 60) return `${s} sec ago`;
    const m = Math.round(s / 60);
    return m < 60 ? `${m} min ago` : `${Math.round(m / 60)} hr ago`;
  };
  const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);

  async function geocode(address) {
    if (!address) return null;
    const key = 'fc_geo_in_' + address.toLowerCase();
    try {
      const cached = sessionStorage.getItem(key);
      if (cached) return JSON.parse(cached);
    } catch (e) { /* ignore */ }
    try {
      const res = await withTimeout(fetch(GEOCODE_URL + encodeURIComponent(address)), 6000);
      const list = await res.json();
      if (!list.length) return null;
      const pos = [Number(list[0].lat), Number(list[0].lon)];
      try { sessionStorage.setItem(key, JSON.stringify(pos)); } catch (e) { /* ignore */ }
      return pos;
    } catch (e) { return null; }
  }

  async function roadRoute(from, to) {
    try {
      const url = `${OSRM_URL}${from[1]},${from[0]};${to[1]},${to[0]}?overview=full&geometries=geojson`;
      const res = await withTimeout(fetch(url), 7000);
      const j = await res.json();
      if (!j.routes || !j.routes.length) return null;
      const r = j.routes[0];
      return {
        points: r.geometry.coordinates.map((c) => [c[1], c[0]]),
        minutes: Math.max(1, Math.round(r.duration / 60)),
        km: Math.round(r.distance / 100) / 10,
      };
    } catch (e) { return null; }
  }

  // ------------------------------------------------------------ map
  function icons() {
    return {
      tech: L.divIcon({
        className: 'lt-tech-icon',
        html: '<div class="lt-tech-pulse"></div><div class="lt-tech-dot"><i class="fa-solid fa-motorcycle"></i></div>',
        iconSize: [46, 46], iconAnchor: [23, 23],
      }),
      home: L.divIcon({
        className: 'lt-home-icon',
        html: '<div class="lt-home-pin"><i class="fa-solid fa-house"></i></div>',
        iconSize: [40, 48], iconAnchor: [20, 46],
      }),
    };
  }

  function ensureMap() {
    if (S.map) { setTimeout(() => S.map.invalidateSize(), 50); return; }
    const el = document.getElementById('ltMap');
    S.map = L.map(el, { zoomControl: false, attributionControl: true }).setView([17.385, 78.4867], 13); // Hyderabad until data arrives
    L.control.zoom({ position: 'topright' }).addTo(S.map);
    L.tileLayer(TILE_URL, { maxZoom: 19, attribution: TILE_CREDIT }).addTo(S.map);
    S.map.on('dragstart zoomstart', (e) => { if (e && e.originalEvent) S.userMoved = true; });
    const btn = document.getElementById('ltRecenter');
    if (btn) btn.onclick = () => { S.userMoved = false; fit(true); };
    setTimeout(() => S.map.invalidateSize(), 100);
  }

  function fit(force) {
    if (!S.map || (S.userMoved && !force)) return;
    const pts = [S.techPos, S.homePos].filter(Boolean);
    // keep both pins clear of the floating card (left side on desktop, below the map on phones)
    const sheet = document.getElementById('ltSheet');
    const beside = sheet && getComputedStyle(sheet).position === 'absolute';
    const padLeft = beside ? sheet.offsetWidth + 50 : 40;
    if (pts.length === 2) S.map.fitBounds(L.latLngBounds(pts), { paddingTopLeft: [padLeft, 50], paddingBottomRight: [50, 50], maxZoom: 16 });
    else if (pts.length === 1) S.map.setView(pts[0], 15);
  }

  /** Glide the technician marker to the new position (like food-delivery apps). */
  function moveTech(to) {
    const I = icons();
    if (!S.tech) {
      S.tech = L.marker(to, { icon: I.tech, zIndexOffset: 1000 }).addTo(S.map);
      S.techPos = to;
      return;
    }
    const from = S.tech.getLatLng();
    const start = [from.lat, from.lng];
    if (meters(start, to) < 2) return;
    const deg = bearing(start, to);
    const dot = S.tech.getElement() && S.tech.getElement().querySelector('.lt-tech-dot i');
    if (dot) dot.style.transform = `rotate(${deg - 90}deg)`;
    cancelAnimationFrame(S.anim);
    const t0 = performance.now(), dur = 1800;
    const step = (now) => {
      const k = Math.min(1, (now - t0) / dur);
      const e = k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2; // ease in-out
      S.tech.setLatLng([start[0] + (to[0] - start[0]) * e, start[1] + (to[1] - start[1]) * e]);
      if (k < 1) S.anim = requestAnimationFrame(step);
    };
    S.anim = requestAnimationFrame(step);
    S.techPos = to;
  }

  function drawStraightLine() {
    if (S.line) S.map.removeLayer(S.line);
    S.line = null;
    if (S.techPos && S.homePos && !S.route) {
      S.line = L.polyline([S.techPos, S.homePos], { color: '#1c6fe5', weight: 4, opacity: 0.6, dashArray: '8 10' }).addTo(S.map);
    }
  }

  async function refreshRoute(status) {
    const moving = status === 'EN_ROUTE' || status === 'ACCEPTED';
    if (!S.techPos || !S.homePos || !moving) {
      if (S.route) { S.map.removeLayer(S.route); S.route = null; }
      if (S.line) { S.map.removeLayer(S.line); S.line = null; }
      S.roadEta = null; S.roadKm = null;
      return;
    }
    const due = Date.now() - S.lastRouteAt > ROUTE_EVERY_MS || meters(S.lastRoutePos, S.techPos) > ROUTE_MOVE_M;
    if (!due) return;
    S.lastRouteAt = Date.now();
    S.lastRoutePos = S.techPos;
    const r = await roadRoute(S.techPos, S.homePos);
    if (!r) { drawStraightLine(); return; }
    if (S.line) { S.map.removeLayer(S.line); S.line = null; }
    if (S.route) S.map.removeLayer(S.route);
    S.route = L.layerGroup([
      L.polyline(r.points, { color: '#0b3d91', weight: 8, opacity: 0.25 }),
      L.polyline(r.points, { color: '#1c6fe5', weight: 5, opacity: 0.95 }),
    ]).addTo(S.map);
    S.roadEta = r.minutes;
    S.roadKm = r.km;
  }

  // ------------------------------------------------------------ bottom sheet
  function headline(t, eta) {
    switch (t.status) {
      case 'ACCEPTED': return { big: 'Technician assigned', small: 'They will start travelling to you soon' };
      case 'EN_ROUTE': return eta != null
        ? { big: eta <= 1 ? 'Arriving now' : `Arriving in ${eta} mins`, small: 'Your technician is on the way' }
        : { big: 'On the way', small: 'Your technician is travelling to you' };
      case 'ARRIVED': return { big: 'Technician has arrived', small: 'Please meet them at your door' };
      case 'IN_PROGRESS': return { big: 'Repair in progress', small: 'Your technician is working on it' };
      case 'COMPLETED': return { big: 'Service completed', small: 'Thanks for choosing FixConnect' };
      default: return { big: t.statusLabel || '', small: '' };
    }
  }

  function renderSheet() {
    const t = S.data;
    const sheet = document.getElementById('ltSheet');
    if (!t || !sheet) return;
    const tech = t.technician || {};
    const eta = S.roadEta != null ? S.roadEta : t.etaMinutes;
    const km = S.roadKm != null ? S.roadKm : t.distanceKm;
    const h = headline(t, eta);
    const idx = Math.max(0, STEPS.findIndex((s) => s.key === t.status));
    const phone = tech.phone ? tech.phone.replace(/\s+/g, '') : '';
    const gmaps = S.techPos && S.homePos
      ? `https://www.google.com/maps/dir/?api=1&origin=${S.techPos[0]},${S.techPos[1]}&destination=${S.homePos[0]},${S.homePos[1]}&travelmode=driving`
      : '';

    sheet.innerHTML = `
      <div class="lt-head">
        <div>
          <div class="lt-big">${esc(h.big)}</div>
          <div class="lt-small">${esc(h.small)}${km != null && t.status === 'EN_ROUTE' ? ` · ${km} km away` : ''}</div>
        </div>
        ${t.status === 'EN_ROUTE' && eta != null ? `<div class="lt-eta"><strong>${eta}</strong><span>min</span></div>` : ''}
      </div>
      <div class="lt-steps">
        ${STEPS.map((s, i) => `
          <div class="lt-step ${i < idx ? 'done' : ''} ${i === idx ? 'now' : ''}">
            <span class="lt-step-dot"><i class="fa-solid ${s.icon}"></i></span>
            <span class="lt-step-label">${s.label}</span>
          </div>`).join('<div class="lt-step-bar"></div>')}
      </div>
      <div class="lt-tech">
        <img src="${FC.techPhoto(tech)}" alt="${esc(tech.fullName || '')}" />
        <div class="lt-tech-info">
          <strong>${esc(tech.fullName || 'Technician')}</strong>
          <small>${esc(tech.category ? tech.category.name : '')}${tech.rating ? ` · ★ ${Number(tech.rating).toFixed(1)}` : ''} · Booking #${esc(t.bookingNo || '')}</small>
          <small class="lt-live" id="ltLive">${liveText()}</small>
        </div>
        <div class="lt-actions">
          ${phone ? `<a class="lt-btn lt-btn-call" href="tel:${esc(phone)}" title="Call"><i class="fa-solid fa-phone"></i></a>` : ''}
          ${phone ? `<a class="lt-btn" href="sms:${esc(phone)}" title="Message"><i class="fa-solid fa-message"></i></a>` : ''}
          ${gmaps ? `<a class="lt-btn" target="_blank" rel="noopener" href="${gmaps}" title="Open in Google Maps"><i class="fa-solid fa-diamond-turn-right"></i></a>` : ''}
        </div>
      </div>
      ${t.destinationAddress ? `<div class="lt-address"><i class="fa-solid fa-house"></i> ${esc(t.destinationAddress)}</div>` : ''}`;
  }

  function liveText() {
    const st = S.data ? S.data.status : null;
    if (st === 'ARRIVED' || st === 'IN_PROGRESS') return '<span class="lt-live-dot"></span> Technician is at your home';
    if (st === 'COMPLETED') return '<span class="lt-live-dot off"></span> Job completed';
    if (st === 'ACCEPTED') return '<span class="lt-live-dot off"></span> Live location starts when the technician sets off';
    const stale = S.updatedAt && (Date.now() - S.updatedAt.getTime()) / 1000 > STALE_AFTER_S;
    const text = !S.techPos ? 'Waiting for technician\'s live location…'
      : stale ? `Location may be outdated · ${ago(S.updatedAt)}`
      : `Live location · updated ${ago(S.updatedAt)}`;
    return `<span class="lt-live-dot ${stale || !S.techPos ? 'off' : ''}"></span> ${esc(text)}`;
  }
  function tickLive() {
    const el = document.getElementById('ltLive');
    if (el) el.innerHTML = liveText();
  }

  // ------------------------------------------------------------ polling loop
  async function poll() {
    if (!S.requestId) return;
    let t;
    try {
      t = await FC.api.get(`/api/customer/requests/${S.requestId}/tracking`);
    } catch (e) {
      const sheet = document.getElementById('ltSheet');
      if (sheet) sheet.innerHTML = `<div class="fc-empty">${esc(e.message)}</div>`;
      stop();
      return;
    }
    // "updated N sec ago": measured by this browser when the value changes, so a server in another
    // time zone can't make it look hours old. First load uses the server time (never in the future).
    if (t.locationUpdatedAt !== (S.data && S.data.locationUpdatedAt)) {
      const server = t.locationUpdatedAt ? FC.parseDate(t.locationUpdatedAt) : null;
      S.updatedAt = !server ? null
        : !S.data ? new Date(Math.min(Date.now(), server.getTime()))
        : new Date();
    }
    S.data = t;

    // home pin (geocode the address text if the request has no coordinates)
    if (!S.homePos) {
      // saved coordinates are used only if they are inside India (older requests may hold a wrong lookup)
      if (inIndia(t.destinationLatitude, t.destinationLongitude)) S.homePos = [t.destinationLatitude, t.destinationLongitude];
      else S.homePos = (await FC.currentPosition(8000)) || (await geocode(t.destinationAddress));
      if (S.homePos) S.home = L.marker(S.homePos, { icon: icons().home }).addTo(S.map).bindTooltip('Your home', { direction: 'top', offset: [0, -40] });
    }

    // technician marker: live GPS while travelling; once arrived / working the technician is at the home
    const atHome = t.status === 'ARRIVED' || t.status === 'IN_PROGRESS' || t.status === 'COMPLETED';
    if (atHome && S.homePos) moveTech(S.homePos);
    else if (t.technicianLatitude != null && t.technicianLongitude != null) moveTech([t.technicianLatitude, t.technicianLongitude]);

    if (!S.fitted && (S.techPos || S.homePos)) { fit(true); S.fitted = true; } else fit(false);
    await refreshRoute(t.status);
    renderSheet();

    if (t.status === 'COMPLETED' || t.status === 'CANCELLED') stop();
  }

  function stop() {
    clearInterval(S.timer); S.timer = null;
    clearInterval(S.tick); S.tick = null;
  }

  function reset() {
    stop();
    cancelAnimationFrame(S.anim);
    if (S.map) {
      [S.tech, S.home, S.route, S.line].forEach((l) => { if (l) S.map.removeLayer(l); });
    }
    Object.assign(S, { tech: null, home: null, route: null, line: null, techPos: null, homePos: null,
      lastRouteAt: 0, lastRoutePos: null, roadEta: null, roadKm: null, updatedAt: null,
      userMoved: false, fitted: false, data: null });
  }

  /** Open live tracking for a request id (null = nothing to track). */
  async function open(requestId) {
    const sheet = document.getElementById('ltSheet');
    if (typeof L === 'undefined') {
      if (sheet) sheet.innerHTML = '<div class="fc-empty">Map library could not be loaded.</div>';
      return;
    }
    ensureMap();
    if (requestId !== S.requestId) reset(); else stop();
    S.requestId = requestId;
    if (!requestId) {
      if (sheet) sheet.innerHTML = '<div class="fc-empty">No active booking to track yet. Live tracking starts when a technician accepts your request.</div>';
      return;
    }
    if (sheet && !S.data) sheet.innerHTML = '<div class="fc-loading"><i class="fa-solid fa-spinner fa-spin"></i> Connecting to live location…</div>';
    await poll();
    if (S.data && !['COMPLETED', 'CANCELLED'].includes(S.data.status)) {
      S.timer = setInterval(poll, POLL_MS);
      S.tick = setInterval(tickLive, 1000); // keeps "updated N sec ago" ticking
    }
  }

  window.LiveTracking = { open, stop };
})();
