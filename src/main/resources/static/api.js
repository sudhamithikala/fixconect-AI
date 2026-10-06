/* ==========================================================================
   FIXCONNECT AI - SHARED API CLIENT
   Talks to the Spring Boot backend (fixconnect-backend).
   - Stores the JWT + user after login (localStorage)
   - fetch wrapper with JSON / multipart / file download support
   - Auth guards, logout, navbar state
   - Small UI helpers: toast, modal form, formatting, status badges
   ========================================================================== */
(function () {
  'use strict';

  // ---------------------------------------------------------------- configuration
  // Pages served by Spring Boot itself - http://localhost:8080, http://<server-ip>:8080 or https://<domain>
  // behind a proxy - call the API on the same address. Only a page opened from a file (file://) or from a
  // local dev server on another port (e.g. VS Code Live Server :5500) calls the backend on localhost:8080.
  const DEV_PAGE = !location.protocol.startsWith('http')
    || (/^(localhost|127\.0\.0\.1)$/.test(location.hostname) && location.port !== '8080');
  const API_BASE = window.FIXCONNECT_API_BASE || (DEV_PAGE ? 'http://localhost:8080' : '');

  const TOKEN_KEY = 'fc_token';
  const USER_KEY = 'fc_user';

  // ---------------------------------------------------------------- storage (safe)
  function store(key, value) {
    try {
      if (value === null || value === undefined) localStorage.removeItem(key);
      else localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
    } catch (e) { /* storage unavailable */ }
  }
  function read(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }

  function getToken() { return read(TOKEN_KEY); }
  function getUser() {
    try { return JSON.parse(read(USER_KEY) || 'null'); } catch (e) { return null; }
  }
  function isLoggedIn() { return !!getToken() && !!getUser(); }

  function saveSession(auth) {
    store(TOKEN_KEY, auth.token);
    store(USER_KEY, auth.user);
  }
  function clearSession() {
    store(TOKEN_KEY, null);
    store(USER_KEY, null);
  }

  // ---------------------------------------------------------------- HTTP
  class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.status = status;
      this.body = body;
    }
  }

  async function request(path, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    const token = getToken();
    if (token && options.auth !== false) headers['Authorization'] = 'Bearer ' + token;

    let body = options.body;
    if (body !== undefined && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }

    let res;
    try {
      res = await fetch(API_BASE + path, { method: options.method || 'GET', headers, body });
    } catch (e) {
      throw new ApiError(0, 'Cannot reach the FixConnect server at ' + (API_BASE || location.origin)
        + '. Is the backend running (mvn spring-boot:run)?');
    }

    if (options.raw) {
      if (!res.ok) throw new ApiError(res.status, 'Download failed (' + res.status + ')');
      return res;
    }

    const text = await res.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = text; }
    }

    if (!res.ok) {
      let msg = (data && data.message) || ('Request failed (' + res.status + ')');
      if (data && data.fieldErrors) {
        msg = Object.entries(data.fieldErrors).map(([k, v]) => k + ': ' + v).join('\n');
      }
      // Expired / invalid session on a protected call -> back to login
      if (res.status === 401 && token && options.auth !== false && !options.noRedirect) {
        clearSession();
        if (/deactivated/i.test(msg)) flash(msg);
        redirectToLogin();
      }
      throw new ApiError(res.status, msg, data);
    }
    return data;
  }

  const api = {
    get: (p, o) => request(p, Object.assign({}, o, { method: 'GET' })),
    post: (p, b, o) => request(p, Object.assign({}, o, { method: 'POST', body: b })),
    put: (p, b, o) => request(p, Object.assign({}, o, { method: 'PUT', body: b })),
    patch: (p, b, o) => request(p, Object.assign({}, o, { method: 'PATCH', body: b })),
    del: (p, o) => request(p, Object.assign({}, o, { method: 'DELETE' })),
    upload: (p, file, extra) => {
      const fd = new FormData();
      fd.append('file', file);
      Object.entries(extra || {}).forEach(([k, v]) => fd.append(k, v));
      return request(p, { method: 'POST', body: fd });
    },
  };

  /** Downloads a protected file (e.g. invoice PDF) with the auth header. */
  async function download(path, fileName) {
    const res = await request(path, { raw: true });
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName || 'download';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  /** Absolute URL for files served by the backend (photos). */
  function fileUrl(path) {
    if (!path) return '';
    return path.startsWith('http') ? path : API_BASE + path;
  }

  // ---------------------------------------------------------------- auth helpers
  function dashboardFor(role) {
    if (role === 'ADMIN') return 'admin-dashboard.html';
    return role === 'PROVIDER' ? 'provider-dashboard.html' : 'customer-dashboard.html';
  }

  function isAdminPage() {
    return /^admin-/.test(location.pathname.split('/').pop() || '');
  }

  function currentPage() {
    const p = location.pathname.split('/').pop() || 'index.html';
    return p + location.hash;
  }

  function redirectToLogin() {
    const next = encodeURIComponent(currentPage());
    location.href = (isAdminPage() ? 'admin-login.html' : 'login.html') + '?next=' + next;
  }

  /** One-time message shown on the next page (e.g. "account deactivated" on the login page). */
  function flash(message) {
    if (message === undefined) {
      const m = read('fc_flash');
      store('fc_flash', null);
      return m;
    }
    store('fc_flash', message);
  }

  /** Guard for protected pages. Returns the user or redirects. */
  function requireAuth(role) {
    const user = getUser();
    if (!getToken() || !user) {
      redirectToLogin();
      return null;
    }
    if (role && user.role !== role) {
      location.href = dashboardFor(user.role);
      return null;
    }
    return user;
  }

  function logout() {
    const wasAdmin = (getUser() || {}).role === 'ADMIN' || isAdminPage();
    clearSession();
    location.href = wasAdmin ? 'admin-login.html' : 'login.html';
  }

  function login(auth) {
    saveSession(auth);
  }

  /** Where to go after login / registration. */
  function afterLoginTarget(auth) {
    const params = new URLSearchParams(location.search);
    const next = params.get('next');
    if (next && !/^https?:|^\/\//i.test(next)) return next;
    if (auth.role === 'CUSTOMER' && read('emergencyType')) return 'customer-dashboard.html#section-emergency';
    return auth.redirectTo || dashboardFor(auth.role);
  }

  // ---------------------------------------------------------------- formatting
  function esc(v) {
    return String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  const inr = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2, minimumFractionDigits: 0 });
  function money(v) {
    if (v === null || v === undefined || v === '') return '—';
    return '₹' + inr.format(Number(v));
  }

  function parseDate(v) {
    if (!v) return null;
    // "2026-10-01" (LocalDate) must be treated as a local date, not UTC
    if (/^\d{4}-\d{2}-\d{2}$/.test(v)) {
      const [y, m, d] = v.split('-').map(Number);
      return new Date(y, m - 1, d);
    }
    return new Date(v);
  }

  function fmtDate(v) {
    const d = parseDate(v);
    if (!d || isNaN(d)) return '—';
    const today = new Date();
    const sameDay = d.toDateString() === today.toDateString();
    if (sameDay) return 'Today';
    const tmr = new Date(today); tmr.setDate(today.getDate() + 1);
    if (d.toDateString() === tmr.toDateString()) return 'Tomorrow';
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  function fmtDateTime(v) {
    const d = parseDate(v);
    if (!d || isNaN(d)) return '—';
    return fmtDate(v) + ', ' + d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  }

  function timeAgo(v) {
    const d = parseDate(v);
    if (!d || isNaN(d)) return '';
    const s = Math.floor((Date.now() - d.getTime()) / 1000);
    if (s < 60) return 'just now';
    if (s < 3600) return Math.floor(s / 60) + ' mins ago';
    if (s < 86400) return Math.floor(s / 3600) + ' hours ago';
    if (s < 172800) return 'Yesterday';
    return fmtDate(v);
  }

  const SLOT_TIME = { MORNING: '09:00 AM', AFTERNOON: '12:00 PM', EVENING: '03:00 PM', IMMEDIATE: 'ASAP' };
  const SLOT_LABEL = {
    MORNING: 'Morning (9 AM - 12 PM)', AFTERNOON: 'Afternoon (12 PM - 3 PM)',
    EVENING: 'Evening (3 PM - 6 PM)', IMMEDIATE: 'Immediate Emergency (SOS)',
  };
  function fmtSlot(date, slot) {
    return fmtDate(date) + (slot ? ', ' + (slot === 'IMMEDIATE' ? 'ASAP' : SLOT_LABEL[slot].split(' (')[0]) : '');
  }

  function stars(rating) {
    const r = Math.round(Number(rating) || 0);
    return '★★★★★'.slice(0, r) + '☆☆☆☆☆'.slice(0, 5 - r);
  }

  const STATUS_BADGE = {
    PENDING: ['badge-warning', 'Awaiting Technician'],
    ACCEPTED: ['badge-scheduled', 'Scheduled'],
    EN_ROUTE: ['badge-ongoing', 'Technician En Route'],
    ARRIVED: ['badge-ongoing', 'Technician Arrived'],
    IN_PROGRESS: ['badge-ongoing', 'In Progress'],
    COMPLETED: ['badge-completed', 'Completed'],
    CANCELLED: ['badge-cancelled', 'Cancelled'],
    PAID: ['badge-completed', 'Paid'],
    UNPAID: ['badge-warning', 'Unpaid'],
    ACTIVE: ['badge-completed', 'Active Warranty'],
    CLAIMED: ['badge-info', 'Claimed'],
    EXPIRED: ['badge-cancelled', 'Expired'],
  };
  function badge(status, labelOverride) {
    const [cls, label] = STATUS_BADGE[status] || ['badge-info', status];
    return '<span class="badge-pill-custom ' + cls + '">' + esc(labelOverride || label) + '</span>';
  }

  // Category helpers -------------------------------------------------
  const CATEGORY_ALIASES = {
    'electrician': 'ELECTRICAL', 'electrical': 'ELECTRICAL', 'electrical repair': 'ELECTRICAL',
    'plumber': 'PLUMBING', 'plumbing': 'PLUMBING', 'plumbing services': 'PLUMBING',
    'ac technician': 'AC_REPAIR', 'ac repair': 'AC_REPAIR', 'ac repair & maintenance': 'AC_REPAIR',
    'carpenter': 'CARPENTRY', 'carpentry': 'CARPENTRY', 'carpentry & furniture': 'CARPENTRY',
    'appliance repair': 'APPLIANCE_REPAIR', 'painter': 'PAINTING', 'house painting': 'PAINTING',
    'cleaning': 'CLEANING', 'home cleaning': 'CLEANING', 'home deep cleaning': 'CLEANING',
    'other': 'GENERAL', 'general inquiry / other': 'GENERAL',
  };
  function categoryCode(label) {
    if (!label) return '';
    const k = String(label).trim().toLowerCase();
    return CATEGORY_ALIASES[k] || String(label).trim().toUpperCase().replace(/[^A-Z]+/g, '_');
  }

  let categoriesCache = null;
  async function categories() {
    if (!categoriesCache) categoriesCache = await api.get('/api/services', { auth: false });
    return categoriesCache;
  }

  // ---------------------------------------------------------------- password rules (same as the server)
  const PASSWORD_HINT = 'Min 8 characters, starting with a capital letter, with a lowercase letter, a number and a symbol (e.g. Fixconnect@1)';
  /** Returns a message describing the first broken rule, or null when the password is valid. */
  function passwordError(pw) {
    pw = String(pw || '');
    if (pw.length < 8) return 'Password must be at least 8 characters long.';
    if (!/^[A-Z]/.test(pw)) return 'Password must start with a capital letter.';
    if (!/[a-z]/.test(pw)) return 'Password must contain at least one lowercase letter.';
    if (!/[0-9]/.test(pw)) return 'Password must contain at least one number.';
    if (!/[^A-Za-z0-9]/.test(pw)) return 'Password must contain at least one symbol (e.g. @ # $ !).';
    return null;
  }

  // ---------------------------------------------------------------- mobile number rule (same as the server)
  const PHONE_RE = /^(?:\+91[\s-]?|0)?[6-9]\d{4}[\s-]?\d{5}$/;
  /** Indian mobile: 10 digits starting 6-9, optional +91/0, spaces or dashes allowed. Returns a message or null. */
  function phoneError(v, optional) {
    v = String(v || '').trim();
    if (!v) return optional ? null : 'Mobile number is required.';
    const digits = v.replace(/\D/g, '').replace(/^(91|0)(?=\d{10}$)/, '');
    if (/[^0-9+\s-]/.test(v)) return 'Mobile number can contain only digits, spaces, - and a leading +91.';
    if (digits.length !== 10) return 'Mobile number must have exactly 10 digits (e.g. 98765 43210).';
    if (!/^[6-9]/.test(digits)) return 'Mobile number must start with 6, 7, 8 or 9.';
    if (!PHONE_RE.test(v)) return 'Enter the mobile number like +91 98765 43210 or 9876543210.';
    return null;
  }

  // ---------------------------------------------------------------- other field rules (same as the server)
  // Each helper returns a friendly message for the first broken rule, or null when the value is fine.
  const NAME_RE = /^[A-Za-z][A-Za-z .'-]{1,59}$/;
  const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
  const ADDRESS_CHARS_RE = /^[A-Za-z0-9\s,./#()&:;'+-]*$/;
  const AREA_CHARS_RE = /^[A-Za-z0-9\s,./&()'-]*$/;
  const CITY_RE = /^[A-Za-z][A-Za-z .-]{1,59}$/;
  const LABEL_RE = /^[A-Za-z][A-Za-z0-9 .'-]{0,39}$/;
  const UPI_RE = /^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$/;
  const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/;
  const str = (v) => String(v == null ? '' : v).trim();

  function nameError(v, label) {
    label = label || 'Name'; v = str(v);
    if (!v) return label + ' is required.';
    if (/\d/.test(v)) return label + ' cannot contain numbers.';
    if (!/^[A-Za-z]/.test(v)) return label + ' must start with a letter.';
    if (v.length < 2) return label + ' must be at least 2 characters.';
    if (v.length > 60) return label + ' must be at most 60 characters.';
    if (!NAME_RE.test(v)) return label + " can contain only letters, spaces, dots ( . ), apostrophes ( ' ) and hyphens ( - ).";
    return null;
  }
  function emailError(v) {
    v = str(v);
    if (!v) return 'E-mail address is required.';
    if (/\s/.test(v)) return 'E-mail address cannot contain spaces.';
    if (v.length > 120) return 'E-mail address is too long.';
    if (!EMAIL_RE.test(v)) return 'Enter a valid e-mail address (e.g. name@example.com).';
    return null;
  }
  function addressError(v, opts) {
    opts = opts || {}; const label = opts.label || 'Address', max = opts.max || 300; v = str(v);
    if (!v) return opts.optional ? null : label + ' is required.';
    if (v.length < 10) return 'Please enter the full ' + label.toLowerCase() + ' (house/flat no., street, area) - at least 10 characters.';
    if (v.length > max) return label + ' must be at most ' + max + ' characters.';
    if (!/[A-Za-z]/.test(v)) return label + ' must include the street / area name, not only numbers.';
    if (!ADDRESS_CHARS_RE.test(v)) return label + " can contain only letters, numbers, spaces and , . / # - ( ) & : ; ' +";
    return null;
  }
  function cityError(v, label) {
    label = label || 'City'; v = str(v);
    if (!v) return null;
    if (!CITY_RE.test(v)) return label + ' must be 2-60 letters (spaces, dots and hyphens allowed).';
    return null;
  }
  function pincodeError(v, optional) {
    v = str(v);
    if (!v) return optional === false ? 'Pincode is required.' : null;
    if (!/^\d{6}$/.test(v)) return 'Pincode must be exactly 6 digits.';
    if (v[0] === '0') return 'Pincode cannot start with 0.';
    return null;
  }
  function areaError(v) {
    v = str(v);
    if (!v) return 'Service area is required.';
    if (v.length < 3 || v.length > 200) return 'Service area must be 3-200 characters.';
    if (!/[A-Za-z]/.test(v)) return 'Service area must contain area names (e.g. Jubilee Hills, Madhapur).';
    if (!AREA_CHARS_RE.test(v)) return "Service area can contain only letters, numbers, spaces and , . / & ( ) ' -";
    return null;
  }
  /** Free text such as descriptions: {label, min, max, optional}. Must contain letters. */
  function textError(v, opts) {
    opts = opts || {}; const label = opts.label || 'This field'; v = str(v);
    if (!v) return opts.optional ? null : label + ' is required.';
    if (opts.min && v.length < opts.min) return label + ' must be at least ' + opts.min + ' characters.';
    if (opts.max && v.length > opts.max) return label + ' must be at most ' + opts.max + ' characters.';
    if (!/[A-Za-z]/.test(v)) return label + ' must be written in words (it must contain letters).';
    return null;
  }
  /** Numbers: {label, min, max, integer, optional}. */
  function numberError(v, opts) {
    opts = opts || {}; const label = opts.label || 'Value'; v = str(v);
    if (!v) return opts.optional ? null : label + ' is required.';
    const n = Number(v);
    if (!isFinite(n)) return label + ' must be a number.';
    if (opts.integer && !Number.isInteger(n)) return label + ' must be a whole number.';
    if (opts.min != null && n < opts.min) return label + ' must be at least ' + opts.min + '.';
    if (opts.max != null && n > opts.max) return label + ' must be at most ' + opts.max + '.';
    return null;
  }
  function labelError(v) {
    v = str(v);
    if (!v) return null;
    if (!LABEL_RE.test(v)) return 'Label must start with a letter and be at most 40 characters (e.g. Home, Office).';
    return null;
  }
  function upiError(v) {
    v = str(v);
    if (!v) return null;
    if (!UPI_RE.test(v)) return 'Enter a valid UPI ID (e.g. rahul@okaxis).';
    return null;
  }
  function ifscError(v) {
    v = str(v).toUpperCase();
    if (!v) return null;
    if (!IFSC_RE.test(v)) return 'IFSC must be 11 characters: 4 letters, 0, then 6 letters/digits (e.g. HDFC0001234).';
    return null;
  }
  function accountNoError(v) {
    v = str(v);
    if (!v) return null;
    if (!/^\d{9,18}$/.test(v)) return 'Account number must be 9-18 digits.';
    return null;
  }
  /** Returns the first non-null message from a list of checks. */
  function firstError() {
    for (const e of arguments) if (e) return e;
    return null;
  }

  // ---------------------------------------------------------------- skeleton loaders
  /* Grey shimmering placeholders shown while data loads, so no fake names or numbers ever appear.
     kind: 'table' | 'list' | 'cards' | 'techcards' (cards without the grid wrapper) | 'form' | 'lines' */
  function skeleton(kind, n) {
    const line = (w, cls) => `<span class="sk sk-line ${cls || ''}" style="width:${w}"></span>`;
    const rep = (k, f) => Array.from({ length: k }, (_, i) => f(i)).join('');
    const techCard = () => `<div class="sk-card" style="background:var(--fcd-bg-ffffff, var(--white,#fff));">
        <span class="sk" style="display:block;height:120px;border-radius:10px;"></span>
        ${line('40%')}${line('70%', 'sk-title')}${line('55%')}<span class="sk sk-btn" style="width:100%;margin-top:8px;"></span></div>`;
    switch (kind) {
      case 'lines': return rep(n || 3, (i) => line(['85%', '65%', '75%', '50%'][i % 4]));
      case 'list': return `<div class="dash-card">${rep(n || 4, () => `<div class="sk-card">
          ${line('35%', 'sk-title')}${line('80%')}${line('55%')}</div>`)}</div>`;
      case 'techcards': return rep(n || 6, techCard);
      case 'cards': return `<div class="grid-3-col">${rep(n || 6, techCard)}</div>`;
      case 'form': return `<div class="dash-card" style="max-width:760px;">
          <span class="sk sk-round" style="width:80px;height:80px;display:block;margin-bottom:18px;"></span>
          ${rep(n || 4, () => `<div style="margin-bottom:16px;">${line('25%')}<span class="sk" style="display:block;height:40px;border-radius:8px;"></span></div>`)}
          <span class="sk sk-btn" style="width:130px;height:40px;"></span></div>`;
      default: return `<div class="dash-card">${line('30%', 'sk-title')}
          <div style="margin-top:14px;">${rep(n || 5, (i) => `<div style="display:flex;gap:16px;align-items:center;padding:12px 0;${i ? 'border-top:1px solid var(--fcd-bd-ecf1f8, var(--slate-200,#e2e8f0));' : ''}">
            <span class="sk" style="width:18%"></span><span class="sk" style="width:22%"></span><span class="sk" style="width:30%"></span>
            <span class="sk sk-pill" style="width:14%"></span></div>`)}</div></div>`;
    }
  }
  /** Ends skeletons that never got data (e.g. the request failed): shimmer boxes become "-". */
  function stopSkeletons(root) {
    root = root || document;
    root.querySelectorAll('.sk-card').forEach((el) => el.remove());
    root.querySelectorAll('.sk').forEach((el) => el.replaceWith(document.createTextNode('-')));
    root.querySelectorAll('.sk-form').forEach((el) => el.classList.remove('sk-form'));
    root.querySelectorAll('.sk-bg').forEach((el) => el.classList.remove('sk-bg'));
  }
  /** Fills every <div data-fc-skeleton="kind" data-n="3"> on the page with a skeleton. */
  function mountSkeletons(root) {
    (root || document).querySelectorAll('[data-fc-skeleton]').forEach((el) => {
      el.innerHTML = skeleton(el.dataset.fcSkeleton, Number(el.dataset.n) || undefined);
      el.removeAttribute('data-fc-skeleton');
    });
  }

  // ---------------------------------------------------------------- user's own location
  /** Asks the browser for the user's current GPS position (needs the user's permission).
      Resolves [lat, lng], or null when denied / unavailable / too slow. Never rejects. */
  function currentPosition(timeoutMs) {
    return new Promise((resolve) => {
      if (!navigator.geolocation) return resolve(null);
      let done = false;
      const finish = (v) => { if (!done) { done = true; resolve(v); } };
      setTimeout(() => finish(null), (timeoutMs || 10000) + 500);
      navigator.geolocation.getCurrentPosition(
        (p) => finish([p.coords.latitude, p.coords.longitude]),
        () => finish(null),
        { enableHighAccuracy: true, timeout: timeoutMs || 10000, maximumAge: 60000 });
    });
  }

  // ---------------------------------------------------------------- profile pictures
  /* Real photo when the user uploaded one, otherwise a coloured circle with their initials.
     No stock or AI images are ever shown for people. */
  const AVATAR_COLORS = ['#1c6fe5', '#0f766e', '#7c3aed', '#c2410c', '#be123c', '#0369a1', '#4d7c0f', '#a16207'];
  function initials(name) {
    const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '?';
    return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }
  function avatarSrc(name, photoUrl) {
    if (photoUrl) return fileUrl(photoUrl);
    const n = String(name || '');
    let h = 0;
    for (const ch of n) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    const bg = AVATAR_COLORS[h % AVATAR_COLORS.length];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120" viewBox="0 0 120 120">`
      + `<rect width="120" height="120" fill="${bg}"/>`
      + `<text x="60" y="60" dy="0.36em" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="46" font-weight="700" fill="#ffffff">${esc(initials(n))}</text></svg>`;
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }
  /** Avatar for a person object ({fullName, photoUrl}); kept under the old name for existing pages. */
  function techPhoto(person) {
    return person && typeof person === 'object' ? avatarSrc(person.fullName, person.photoUrl) : avatarSrc('', null);
  }

  /**
   * Adds "Upload photo" / "Remove" buttons for the logged-in user's own picture.
   * imgEl  - the <img> showing the picture; holder - element the buttons are appended to.
   */
  function mountPhotoControls(imgEl, holder, me) {
    if (!imgEl || !holder || holder.querySelector('.fc-photo-ctrls')) return;
    let user = me || getUser() || {};
    const paint = () => {
      imgEl.src = avatarSrc(user.fullName, user.photoUrl);
      imgEl.alt = user.fullName || 'Profile picture';
      remove.style.display = user.photoUrl ? '' : 'none';
      pick.innerHTML = `<i class="fa-solid fa-camera"></i> ${user.photoUrl ? 'Change photo' : 'Upload photo'}`;
    };
    const box = document.createElement('div');
    box.className = 'fc-photo-ctrls';
    box.style.cssText = 'display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;';
    const input = document.createElement('input');
    input.type = 'file'; input.accept = 'image/jpeg,image/png,image/webp,image/gif'; input.hidden = true;
    const pick = document.createElement('button');
    pick.type = 'button'; pick.className = 'btn-secondary-custom btn-sm';
    const remove = document.createElement('button');
    remove.type = 'button'; remove.className = 'btn-secondary-custom btn-sm';
    remove.innerHTML = '<i class="fa-solid fa-trash"></i> Remove';
    box.append(pick, remove, input);
    holder.appendChild(box);

    const done = (updated, msg) => {
      user = updated;
      const stored = getUser();
      if (stored && stored.id === updated.id) store(USER_KEY, Object.assign(stored, { photoUrl: updated.photoUrl }));
      document.querySelectorAll('img.fc-my-avatar').forEach((i) => { i.src = avatarSrc(updated.fullName, updated.photoUrl); });
      paint();
      toast(msg, 'success');
    };
    pick.onclick = () => input.click();
    input.onchange = async () => {
      const file = input.files[0];
      input.value = '';
      if (!file) return;
      if (!/^image\/(jpeg|png|webp|gif)$/.test(file.type)) return toast('Choose a JPG, PNG, WEBP or GIF image', 'error');
      if (file.size > 5 * 1024 * 1024) return toast('Photo must be 5 MB or smaller', 'error');
      try {
        pick.disabled = true;
        done(await api.upload('/api/users/me/photo', file), 'Profile photo updated');
      } catch (e) { showError(e); } finally { pick.disabled = false; }
    };
    remove.onclick = async () => {
      try { done(await api.del('/api/users/me/photo'), 'Profile photo removed'); } catch (e) { showError(e); }
    };
    paint();
    // refresh from the server in case the stored session is older than the last upload
    api.get('/api/users/me').then((u) => { user = u; paint(); }).catch(() => {});
  }

  // ---------------------------------------------------------------- UI: styles for toast + modal
  function injectStyles() {
    if (document.getElementById('fc-api-styles')) return;
    const css = `
      .fc-toast-wrap{position:fixed;right:18px;bottom:18px;z-index:9999;display:flex;flex-direction:column;gap:10px;max-width:min(380px,calc(100vw - 36px))}
      .fc-toast{background:#0f172a;color:#fff;padding:12px 16px;border-radius:12px;box-shadow:0 10px 30px rgba(15,23,42,.25);font:600 .88rem/1.4 Inter,system-ui,sans-serif;white-space:pre-line;animation:fcIn .2s ease-out}
      .fc-toast{display:flex;align-items:flex-start;gap:12px}.fc-toast>span{flex:1}.fc-toast-x{background:none;border:0;color:inherit;opacity:.8;font-size:1.3rem;line-height:1;cursor:pointer;padding:0 2px;margin:-2px -4px 0 0}.fc-toast-x:hover{opacity:1}.fc-toast.success{background:#166534}.fc-toast.error{background:#b91c1c}.fc-toast.info{background:#1d4ed8}
      @keyframes fcIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:none}}
      .fc-modal-backdrop{position:fixed;inset:0;background:rgba(15,23,42,.55);z-index:9998;display:flex;align-items:center;justify-content:center;padding:16px}
      .fc-modal{background:var(--fcd-bg-ffffff, #fff);border-radius:18px;max-width:520px;width:100%;max-height:90vh;overflow:auto;padding:22px;box-shadow:0 25px 60px rgba(15,23,42,.35);font-family:Inter,system-ui,sans-serif}
      .fc-modal h3{margin:0 0 4px;color:var(--fcd-tx-0b1b3f, #0b1b3f);font-size:1.15rem}.fc-modal p.fc-sub{margin:0 0 14px;color:var(--fcd-tx-64748b, #64748b);font-size:.85rem;white-space:pre-line;line-height:1.5}
      .fc-modal label{display:block;font-weight:700;font-size:.8rem;color:var(--fcd-tx-334155, #334155);margin:10px 0 4px}
      .fc-modal input,.fc-modal select,.fc-modal textarea{width:100%;box-sizing:border-box;padding:10px 12px;border:1px solid var(--fcd-bd-cbd5e1, #cbd5e1);border-radius:10px;font:inherit;font-size:.9rem}
      .fc-modal textarea{min-height:80px;resize:vertical}
      .fc-modal .fc-actions{display:flex;gap:10px;justify-content:flex-end;margin-top:18px}
      .fc-empty{padding:18px;text-align:center;color:var(--fcd-tx-64748b, #64748b);font-size:.9rem}
      .fc-loading{padding:18px;text-align:center;color:var(--fcd-tx-64748b, #64748b);font-size:.9rem}
      .fc-list-item{background:var(--fcd-bg-f8fafc, var(--slate-50,#f8fafc));border:1px solid var(--fcd-bd-ecf1f8, var(--slate-200,#e2e8f0));border-radius:var(--radius-md,12px);padding:14px;margin-bottom:12px}
      .fc-row{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}
      .fc-muted{color:var(--fcd-tx-5f6f87, var(--slate-500,#64748b));font-size:.84rem;margin:2px 0 0}
      .fc-bar{height:8px;background:var(--fcd-bg-ecf1f8, var(--slate-200,#e2e8f0));border-radius:4px;overflow:hidden}
      .fc-bar>div{height:100%;background:var(--blue-700,#1d4ed8)}
      .fc-photo-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}
      .fc-photo-grid figure{margin:0;border-radius:12px;overflow:hidden;border:1px solid var(--fcd-bd-e2e8f0, #e2e8f0);background:var(--fcd-bg-ffffff, #fff)}
      .fc-photo-grid img{width:100%;height:130px;object-fit:cover;display:block}
      .fc-photo-grid figcaption{padding:6px 10px;font-size:.75rem;font-weight:700;color:var(--fcd-tx-334155, #334155)}
      .fc-filter-bar{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:18px}
      .fc-filter-bar select,.fc-filter-bar input{padding:9px 12px;border:1px solid var(--fcd-bd-cbd5e1, #cbd5e1);border-radius:10px;font:inherit;font-size:.88rem;background:var(--fcd-bg-ffffff, #fff)}
    `;
    const style = document.createElement('style');
    style.id = 'fc-api-styles';
    style.textContent = css;
    document.head.appendChild(style);
  }

  function toast(message, type) {
    injectStyles();
    let wrap = document.querySelector('.fc-toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'fc-toast-wrap';
      document.body.appendChild(wrap);
    }
    const el = document.createElement('div');
    el.className = 'fc-toast ' + (type || 'info');
    const text = document.createElement('span');
    text.textContent = message;
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'fc-toast-x';
    close.setAttribute('aria-label', 'Close');
    close.innerHTML = '&times;';
    close.onclick = () => el.remove();
    el.append(text, close);
    wrap.appendChild(el);
    setTimeout(() => el.remove(), type === 'error' ? 6000 : 3500);
  }

  function showError(err) {
    console.error(err);
    toast(err && err.message ? err.message : String(err), 'error');
  }

  /**
   * Simple modal form. fields: [{name,label,type,options:[{value,label}],value,required,placeholder,min,max}]
   * Resolves with an object of values, or null if cancelled.
   */
  function modal({ title, subtitle, fields = [], submitText = 'Save', danger = false, validate = null }) {
    injectStyles();
    return new Promise((resolve) => {
      const back = document.createElement('div');
      back.className = 'fc-modal-backdrop';
      const inputs = fields.map((f) => {
        const id = 'fcm_' + f.name;
        const req = f.required ? 'required' : '';
        const attrs = [f.min !== undefined ? `min="${esc(f.min)}"` : '', f.max !== undefined ? `max="${esc(f.max)}"` : '',
          f.step ? `step="${esc(f.step)}"` : '', f.placeholder ? `placeholder="${esc(f.placeholder)}"` : '',
          f.accept ? `accept="${esc(f.accept)}"` : ''].join(' ');
        let control;
        if (f.type === 'select') {
          control = `<select id="${id}" name="${esc(f.name)}" ${req}>` + (f.options || []).map((o) =>
            `<option value="${esc(o.value)}" ${String(o.value) === String(f.value ?? '') ? 'selected' : ''}>${esc(o.label)}</option>`).join('') + '</select>';
        } else if (f.type === 'textarea') {
          control = `<textarea id="${id}" name="${esc(f.name)}" ${req} ${attrs}>${esc(f.value ?? '')}</textarea>`;
        } else {
          control = `<input id="${id}" name="${esc(f.name)}" type="${esc(f.type || 'text')}" value="${f.type === 'file' ? '' : esc(f.value ?? '')}" ${req} ${attrs} />`;
        }
        return `<label for="${id}">${esc(f.label)}</label>${control}`;
      }).join('');
      back.innerHTML = `<form class="fc-modal" novalidate>
          <h3>${esc(title)}</h3>${subtitle ? `<p class="fc-sub">${esc(subtitle)}</p>` : ''}
          ${inputs}
          <div class="fc-modal-err" style="display:none;color:var(--fcd-tx-b91c1c, #b91c1c);background:var(--fcd-bg-fef2f2, #fef2f2);border:1px solid var(--fcd-bd-fecaca, #fecaca);border-radius:8px;padding:8px 12px;margin-top:10px;font-size:.85rem;font-weight:600;"></div>
          <div class="fc-actions">
            <button type="button" class="btn-secondary-custom" data-cancel>Cancel</button>
            <button type="submit" class="${danger ? 'btn-danger-custom' : 'btn-primary-custom'}">${esc(submitText)}</button>
          </div></form>`;
      document.body.appendChild(back);
      const form = back.querySelector('form');
      const close = (val) => { back.remove(); resolve(val); };
      back.querySelector('[data-cancel]').onclick = () => close(null);
      back.addEventListener('click', (e) => { if (e.target === back) close(null); });
      form.addEventListener('submit', (e) => {
        e.preventDefault();
        if (!form.reportValidity()) return;
        const out = {};
        fields.forEach((f) => {
          const el = form.querySelector('#fcm_' + f.name);
          out[f.name] = f.type === 'file' ? (el.files[0] || null) : f.type === 'password' ? el.value : el.value.trim();
        });
        const msg = validate ? validate(out) : null;
        const box = form.querySelector('.fc-modal-err');
        if (msg) { box.textContent = msg; box.style.display = 'block'; return; }
        close(out);
      });
      const first = form.querySelector('input,select,textarea');
      if (first) first.focus();
    });
  }

  function confirmDialog(title, subtitle, okText, danger) {
    return modal({ title, subtitle, fields: [], submitText: okText || 'Confirm', danger }).then((v) => v !== null);
  }

  /** Disables a button while an async action runs. */
  async function busy(button, fn) {
    if (!button) return fn();
    const old = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Please wait...';
    try { return await fn(); } finally { button.disabled = false; button.innerHTML = old; }
  }

  // ---------------------------------------------------------------- navbar / logout wiring
  function wireChrome() {
    document.querySelectorAll('.logout-link').forEach((a) => {
      a.addEventListener('click', (e) => { e.preventDefault(); logout(); });
    });
    const user = getUser();
    if (!user || !getToken()) return;
    // Public pages: turn "Login" into "Dashboard" and hide "Sign Up" (Logout lives in the dashboard sidebar)
    document.querySelectorAll('.main-nav a[href="login.html"]').forEach((a) => {
      a.textContent = 'Dashboard';
      a.href = dashboardFor(user.role);
    });
    document.querySelectorAll('.main-nav a[href="signup.html"]').forEach((a) => a.remove());
    document.querySelectorAll('.sidebar-user-name').forEach((el) => { el.textContent = user.fullName; });
  }

  document.addEventListener('DOMContentLoaded', () => {
    injectStyles();
    wireChrome();
  });

  // ---------------------------------------------------------------- export
  window.FixConnect = {
    API_BASE, api, download, fileUrl, ApiError,
    getToken, getUser, isLoggedIn, login, logout, saveSession, clearSession,
    requireAuth, redirectToLogin, dashboardFor, afterLoginTarget, flash,
    esc, money, fmtDate, fmtDateTime, fmtSlot, timeAgo, parseDate, stars, badge,
    SLOT_LABEL, SLOT_TIME, categoryCode, categories, techPhoto, avatarSrc, initials, mountPhotoControls, currentPosition, passwordError, PASSWORD_HINT, phoneError,
    skeleton, mountSkeletons, stopSkeletons,
    nameError, emailError, addressError, cityError, pincodeError, areaError, textError, numberError, labelError, upiError, ifscError, accountNoError, firstError,
    toast, showError, modal, confirmDialog, busy,
    store, read,
  };
  // Pages load api.js at the end of <body>, so the markup is already there.
  mountSkeletons();
})();
