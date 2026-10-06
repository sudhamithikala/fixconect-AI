/* ==========================================================================
   FIXCONNECT AI - CUSTOMER DASHBOARD (connected to the Spring Boot API)
   Every sidebar module loads live data from /api/customer/** when opened.
   Requires api.js (window.FixConnect).
   ========================================================================== */

const FC = window.FixConnect;
const { api, esc, money, fmtDate, fmtDateTime, fmtSlot, timeAgo, stars, badge, toast, showError, modal } = FC;

const ME = FC.requireAuth('CUSTOMER');

const state = {
  dashboard: null,
  requests: [],
  addresses: [],
  categories: [],
  bookTechnician: null,   // {id, fullName, category} when "Book Service" was clicked
  techCache: {},          // technician id -> {id, fullName, category} for inline buttons
  trackingTimer: null,
};

// ---------------------------------------------------------------- bootstrap
document.addEventListener('DOMContentLoaded', () => {
  if (!ME) return;
  initSidebarNavigation();
  initMobileDrawer();
  initRequestForm();
  loadDashboard().catch((e) => { showError(e); FC.stopSkeletons(document); });

  const hash = location.hash.replace('#section-', '').replace('#', '');
  if (hash && document.getElementById('section-' + hash)) switchSection(hash);

  // Finish an SOS started on emergency.html before login
  if (FC.read('emergencyType')) switchSection('emergency');
});

// ---------------------------------------------------------------- navigation
function initSidebarNavigation() {
  document.querySelectorAll('.sidebar-link[data-section]').forEach((link) => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      switchSection(link.getAttribute('data-section'));
    });
  });
}

function switchSection(sectionId) {
  document.querySelectorAll('.sidebar-link[data-section]').forEach((l) => l.classList.remove('active'));
  document.querySelectorAll('.dashboard-section').forEach((s) => s.classList.remove('active'));

  const targetLink = document.querySelector(`.sidebar-link[data-section="${sectionId}"]`);
  if (targetLink) targetLink.classList.add('active');
  const targetSection = document.getElementById(`section-${sectionId}`);
  if (targetSection) {
    targetSection.classList.add('active');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  const sidebar = document.getElementById('sidebar');
  if (sidebar) sidebar.classList.remove('mobile-open');
  if (history.replaceState) history.replaceState(null, '', '#section-' + sectionId);

  clearInterval(state.trackingTimer);
  if (window.LiveTracking && sectionId !== 'tracking') window.LiveTracking.stop();
  const loader = LOADERS[sectionId];
  if (loader) loader().catch((e) => { showError(e); failSection(sectionId); });
}

function initMobileDrawer() {
  const toggleBtn = document.getElementById('sidebarToggle');
  const sidebar = document.getElementById('sidebar');
  if (toggleBtn && sidebar) toggleBtn.addEventListener('click', () => sidebar.classList.toggle('mobile-open'));
}

// ---------------------------------------------------------------- render helpers
/** Replace everything in a section below its header. */
function setBody(sectionId, html) {
  const section = document.getElementById('section-' + sectionId);
  if (!section) return null;
  [...section.children].forEach((c) => { if (!c.classList.contains('section-top-header')) c.remove(); });
  section.insertAdjacentHTML('beforeend', html);
  labelTables(section);
  return section;
}

/** On phones each table row becomes a card; give every cell its column name. */
function labelTables(root) {
  root.querySelectorAll('table.dash-table').forEach((t) => {
    const heads = [...t.querySelectorAll('thead th')].map((th) => th.textContent.trim());
    t.querySelectorAll('tbody tr').forEach((tr) => {
      [...tr.children].forEach((td, i) => { if (heads[i] && !td.hasAttribute('colspan')) td.setAttribute('data-label', heads[i]); });
    });
  });
}

/** Which skeleton shape each section shows while loading. */
const SK_KIND = { technicians: ['cards', 6], photos: ['cards', 3], ongoing: ['list', 2], eta: ['list', 2], notifications: ['list', 5],
  profile: ['form', 5], settings: ['list', 3] };
const loading = (id) => setBody(id, FC.skeleton(...(SK_KIND[id] || ['table'])));
/** A section whose data failed to load: say so and offer a retry instead of an endless skeleton. */
function failSection(id) {
  const sec = document.getElementById('section-' + id);
  if (!sec || !sec.querySelector('.sk')) return;
  setBody(id, `<div class="dash-card">${empty('Could not load this section.')}
    <div style="text-align:center;"><button class="btn-secondary-custom btn-sm" onclick="switchSection('${id}')"><i class="fa-solid fa-rotate-right"></i> Try again</button></div></div>`);
}
const empty = (text) => `<div class="fc-empty">${esc(text)}</div>`;

function setBadge(section, count) {
  const link = document.querySelector(`.sidebar-link[data-section="${section}"]`);
  if (!link) return;
  let b = link.querySelector('.nav-badge');
  if (!count) { if (b) b.remove(); return; }
  if (!b) { b = document.createElement('span'); b.className = 'nav-badge'; link.appendChild(b); }
  b.textContent = count;
}

function techCard(t, opts = {}) {
  state.techCache[t.id] = { id: t.id, fullName: t.fullName, category: t.category ? t.category.code : '' };
  const avail = t.availabilityLabel || (t.available ? 'Available Now' : 'Offline');
  const availStyle = avail === 'Available Now'
    ? 'background: var(--green-100); color: var(--green-600);'
    : avail === 'On Active Job' ? 'background: var(--amber-100); color: var(--amber-600);' : '';
  return `
    <div class="technician-card-pro">
      <div class="tech-card-image-wrap">
        <img src="${FC.techPhoto(t)}" alt="${esc(t.fullName)}" />
        ${t.verified ? '<span class="tech-verified-badge"><i class="fa-solid fa-circle-check"></i> Verified</span>' : ''}
      </div>
      <div class="tech-card-body">
        <span class="tech-category-tag">${esc(t.category ? t.category.name : '')}</span>
        <h3>${esc(t.fullName)}</h3>
        <div class="tech-rating-row">
          <span class="stars">${stars(t.rating)}</span> <strong>${t.rating.toFixed(1)}</strong>
          <span class="reviews-count">(${t.reviewCount} reviews)</span>
        </div>
        ${opts.note ? `<p style="font-size:0.82rem;color:var(--fcd-tx-5f6f87, var(--slate-500));">${esc(opts.note)}</p>` : ''}
        <div class="tech-info-pills">
          <span class="tech-pill"><i class="fa-solid fa-briefcase"></i> ${t.experienceYears}+ Yrs Exp</span>
          ${t.distanceKm != null ? `<span class="tech-pill"><i class="fa-solid fa-location-dot"></i> ${t.distanceKm} km</span>` : ''}
          ${t.startingRate != null ? `<span class="tech-pill">From ${money(t.startingRate)}</span>` : ''}
          <span class="tech-pill" style="${availStyle}">${esc(avail)}</span>
        </div>
        <div class="tech-card-footer">
          <button class="btn-secondary-custom btn-sm" onclick="viewTechnician(${t.id})">View Profile</button>
          <button class="btn-primary-custom btn-sm" style="flex: 1;" onclick="bookTechnicianById(${t.id})">${opts.bookText || 'Book Service'}</button>
        </div>
      </div>
    </div>`;
}

function requestActions(r) {
  const a = [];
  if (['ACCEPTED', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS'].includes(r.status)) {
    a.push(`<button class="btn-primary-custom btn-sm" onclick="openTracking(${r.id})">Track Live</button>`);
  }
  if (['PENDING', 'ACCEPTED'].includes(r.status)) {
    a.push(`<button class="btn-secondary-custom btn-sm" onclick="rescheduleRequest(${r.id})">Reschedule</button>`);
  }
  if (['PENDING', 'ACCEPTED', 'EN_ROUTE'].includes(r.status)) {
    a.push(`<button class="btn-secondary-custom btn-sm" onclick="cancelRequest(${r.id})">Cancel</button>`);
  }
  if (r.invoiceId) a.push(`<button class="btn-secondary-custom btn-sm" onclick="switchSection('invoices')">Invoice</button>`);
  if (r.status === 'COMPLETED' && !r.rating && r.technician) {
    a.push(`<button class="btn-secondary-custom btn-sm" onclick="reviewRequest(${r.id})">Rate</button>`);
  }
  return `<div style="display:flex;gap:6px;flex-wrap:wrap;">${a.join('') || '—'}</div>`;
}

// ---------------------------------------------------------------- 1. dashboard home
async function loadDashboard() {
  const d = await api.get('/api/customer/dashboard');
  state.dashboard = d;
  const s = d.stats;
  applyBadges(s);

  document.querySelectorAll('.sidebar-user-name').forEach((el) => { el.textContent = d.fullName; });
  const h1 = document.querySelector('#section-dashboard .section-top-header h1');
  if (h1) h1.textContent = `Welcome back, ${d.fullName.split(' ')[0]} 👋`;

  const cards = document.querySelectorAll('#section-dashboard .stats-grid-4 .stat-card-modern');
  const statData = [
    [s.activeBookings, d.ongoingService ? '1 ongoing right now' : 'Scheduled visits'],
    [s.pendingRequests, 'Awaiting technician'],
    [s.servicesDone, 'All time total'],
    [s.unreadNotifications, 'Unread updates'],
  ];
  cards.forEach((c, i) => {
    if (!statData[i]) return;
    c.querySelector('strong').textContent = String(statData[i][0]).padStart(2, '0');
    c.querySelector('.stat-subtext').textContent = statData[i][1];
  });

  // Ongoing service card
  const card = document.querySelector('#section-dashboard .active-service-card');
  if (card) {
    const o = d.ongoingService;
    if (!o) {
      card.style.display = 'none';
    } else {
      card.style.display = '';
      const t = o.technician || {};
      card.innerHTML = `
        <div class="active-service-content">
          <img src="${FC.techPhoto(t)}" alt="${esc(t.fullName)}" class="active-tech-img" />
          <div class="active-service-info">
            <span class="badge-pill-custom badge-ongoing"><i class="fa-solid fa-spinner fa-spin"></i> ${esc(o.statusLabel)}</span>
            <h3>${esc(t.fullName || '')} · ${esc(o.title)}</h3>
            <p>${esc(o.address)}</p>
            <div class="active-service-meta">
              <span><i class="fa-solid fa-star" style="color: var(--fcd-tx-f59e0b, #f59e0b);"></i> ${t.rating ?? '-'} (${t.reviewCount ?? 0} reviews)</span>
              <span><i class="fa-solid fa-briefcase"></i> ${t.experienceYears ?? '-'}+ Yrs Exp</span>
              ${o.distanceKm != null ? `<span><i class="fa-solid fa-location-dot"></i> ${o.distanceKm} km away</span>` : ''}
              ${o.etaMinutes != null ? `<span><i class="fa-solid fa-clock"></i> ETA ${o.etaMinutes} mins</span>` : ''}
            </div>
          </div>
          <div class="active-service-actions">
            <button class="btn-primary-custom" onclick="openTracking(${o.id})"><i class="fa-solid fa-location-crosshairs"></i> Track Live</button>
            ${t.phone ? `<a href="tel:${esc(t.phone.replace(/\s+/g, ''))}" class="btn-secondary-custom"><i class="fa-solid fa-phone"></i> Call Tech</a>` : ''}
          </div>
        </div>`;
    }
  }

  // Upcoming table
  const tbody = document.querySelector('#section-dashboard .dash-table tbody');
  if (tbody) {
    tbody.innerHTML = d.upcomingServices.length ? d.upcomingServices.map((r) => `
      <tr>
        <td><strong>${esc(r.title)}</strong><br><small class="text-muted">${esc(r.category.name)}</small></td>
        <td>${esc(r.technician ? r.technician.fullName : 'Matching...')}</td>
        <td>${fmtSlot(r.preferredDate, r.timeSlot)}</td>
        <td>${badge(r.status)}</td>
        <td>${['EN_ROUTE', 'ARRIVED', 'IN_PROGRESS', 'ACCEPTED'].includes(r.status)
          ? `<button class="btn-secondary-custom btn-sm" onclick="openTracking(${r.id})">Track</button>`
          : `<button class="btn-secondary-custom btn-sm" onclick="switchSection('complaints')">Details</button>`}</td>
      </tr>`).join('') : `<tr><td colspan="5">${empty('No upcoming services. Request one anytime!')}</td></tr>`;
  }

  // Top technicians
  const grid = document.querySelector('#section-dashboard .grid-2-col');
  if (grid) grid.innerHTML = d.topTechnicians.slice(0, 2).map((t) => techCard(t)).join('') || empty('No technicians yet.');

  // Notifications widget
  const notifCard = [...document.querySelectorAll('#section-dashboard .dash-card')]
    .find((c) => c.querySelector('.dash-card-title')?.textContent.includes('Notifications'));
  if (notifCard) {
    const list = notifCard.querySelector('div[style*="flex-direction: column"]');
    if (list) list.innerHTML = d.recentNotifications.length ? d.recentNotifications.map((n, i) => `
      <div style="display: flex; gap: 10px; font-size: 0.85rem; ${i ? 'padding-top: 10px; border-top: 1px solid var(--fcd-bd-ecf1f8, var(--slate-200));' : ''}">
        <i class="fa-solid ${n.read ? 'fa-circle-check' : 'fa-bell'}" style="color: ${n.read ? 'var(--green-600)' : 'var(--blue-700)'}; margin-top: 3px;"></i>
        <div><strong>${esc(n.title)}</strong>
          <p style="margin: 2px 0 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.78rem;">${esc(n.message)}</p>
          <small style="color: var(--slate-400);">${timeAgo(n.createdAt)}</small></div>
      </div>`).join('') : empty('No notifications.');
  }

  // Quick link points
  document.querySelectorAll('#section-dashboard button').forEach((b) => {
    if (b.textContent.includes('Loyalty Rewards')) {
      b.innerHTML = `<i class="fa-solid fa-award" style="color: var(--fcd-tx-9333ea, var(--purple-600));"></i> Loyalty Rewards (${s.loyaltyPoints} pts)`;
    }
  });

}

/** Sidebar counts come only from the server: upcoming visits, unread notifications. */
function applyBadges(s) {
  const open = (s.activeBookings || 0) + (s.pendingRequests || 0);
  setBadge('upcoming', open);
  setBadge('notifications', s.unreadNotifications || 0);
}
async function refreshBadges() {
  try { applyBadges((await api.get('/api/customer/dashboard')).stats); } catch (e) { /* keep last values */ }
}
setInterval(() => { if (ME && document.visibilityState === 'visible') refreshBadges(); }, 60000);

// ---------------------------------------------------------------- 2. request a service
async function initRequestForm() {
  try {
    const [cats, addresses] = await Promise.all([FC.categories(), api.get('/api/customer/addresses')]);
    state.categories = cats;
    state.addresses = addresses;

    const sel = document.getElementById('serviceCategory');
    sel.innerHTML = '<option value="">Select Service Category</option>'
      + cats.map((c) => `<option value="${c.code}">${esc(c.name)}</option>`).join('');

    const time = document.getElementById('prefTime');
    time.innerHTML = Object.entries(FC.SLOT_LABEL).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('');

    const date = document.getElementById('prefDate');
    const today = new Date();
    const iso = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    date.min = iso(today);
    if (!date.value) { const t = new Date(today); t.setDate(t.getDate() + 1); date.value = iso(t); }

    const loc = document.getElementById('locationInput');
    const primary = addresses.find((a) => a.primary) || addresses[0];
    if (primary) loc.value = primary.fullAddress;
    let dl = document.getElementById('savedAddresses');
    if (!dl) { dl = document.createElement('datalist'); dl.id = 'savedAddresses'; loc.after(dl); }
    dl.innerHTML = addresses.map((a) => `<option value="${esc(a.fullAddress)}">${esc(a.label)}</option>`).join('');
    loc.setAttribute('list', 'savedAddresses');

    const pre = FC.read('selectedService');
    if (pre) { sel.value = pre; FC.store('selectedService', null); }
  } catch (e) { showError(e); }
}

function previewUpload(event) {
  const file = event.target.files[0];
  const previewDiv = document.getElementById('uploadPreview');
  if (file && previewDiv) {
    previewDiv.innerHTML = `<i class="fa-solid fa-file-image"></i> Attached: ${esc(file.name)} (${(file.size / 1024).toFixed(1)} KB)`;
  }
}

function showBookingBanner() {
  const form = document.getElementById('serviceRequestForm');
  let banner = document.getElementById('bookTechBanner');
  if (!state.bookTechnician) { if (banner) banner.remove(); return; }
  if (!banner) {
    banner = document.createElement('div');
    banner.id = 'bookTechBanner';
    banner.className = 'fc-list-item fc-row';
    form.prepend(banner);
  }
  banner.innerHTML = `<span><i class="fa-solid fa-user-check" style="color: var(--fcd-tx-1c6fe5, var(--blue-700));"></i>
      Booking directly with <strong>${esc(state.bookTechnician.fullName)}</strong></span>
    <button type="button" class="btn-secondary-custom btn-sm" onclick="clearBookTechnician()">Any technician</button>`;
}

function clearBookTechnician() {
  state.bookTechnician = null;
  showBookingBanner();
}

function bookTechnicianById(id) {
  bookTechnician(state.techCache[id]);
}

function bookTechnician(t) {
  state.bookTechnician = t;
  switchSection('request');
  if (t.category) document.getElementById('serviceCategory').value = t.category;
  showBookingBanner();
}

async function handleRequestSubmit(event) {
  event.preventDefault();
  const btn = event.target.querySelector('button[type="submit"]');
  const category = document.getElementById('serviceCategory').value;
  const description = document.getElementById('complaintDesc').value.trim();
  const locationText = document.getElementById('locationInput').value.trim();
  if (!category) return toast('Please choose a service category.', 'error');
  const descErr = FC.textError(description, { label: 'Problem description', min: 10, max: 2000 });
  if (descErr) return toast(descErr, 'error');
  if (!document.getElementById('prefDate').value) return toast('Please choose a preferred date.', 'error');
  if (document.getElementById('prefDate').value < new Date().toISOString().slice(0, 10)) return toast('Preferred date cannot be in the past.', 'error');

  const saved = state.addresses.find((a) => a.fullAddress === locationText);
  if (!saved) {
    const addrErr = FC.addressError(locationText, { label: 'Service address', max: 400 });
    if (addrErr) return toast(addrErr, 'error');
  }
  const body = {
    categoryCode: category,
    description,
    addressId: saved ? saved.id : null,
    address: saved ? null : locationText,
    preferredDate: document.getElementById('prefDate').value,
    timeSlot: document.getElementById('prefTime').value,
    emergency: document.getElementById('emergencyPriority').checked,
    technicianId: state.bookTechnician ? state.bookTechnician.id : null,
  };

  await FC.busy(btn, async () => {
    try {
      // Home pin for live tracking: the customer's own device location (they allow it in the browser).
      // Only if that is denied/unavailable do we look up the typed address (limited to India).
      let pos = await FC.currentPosition(10000);
      if (!pos && !saved && locationText) pos = await geocodeAddress(locationText);
      if (pos) { body.latitude = pos[0]; body.longitude = pos[1]; }
      const r = await api.post('/api/customer/requests', body);
      const photo = document.getElementById('photoInput').files[0];
      if (photo) {
        try { await api.upload(`/api/customer/requests/${r.id}/photos`, photo); }
        catch (e) { toast('Request created, but the photo upload failed: ' + e.message, 'error'); }
      }
      toast(`Service request submitted! Ticket #${r.ticketNo}\n` +
        (r.technician ? `Waiting for ${r.technician.fullName} to accept.` : 'Nearby technicians have been notified.'), 'success');
      event.target.reset();
      document.getElementById('uploadPreview').innerHTML = '';
      state.bookTechnician = null;
      showBookingBanner();
      await initRequestForm();
      loadDashboard().catch(() => {});
      switchSection('complaints');
    } catch (e) { showError(e); }
  });
}

// ---------------------------------------------------------------- 3. complaints
async function loadComplaints() {
  loading('complaints');
  const list = await api.get('/api/customer/requests?scope=ALL');
  state.requests = list;
  setBody('complaints', `
    <div class="dash-card"><div class="dash-table-container"><table class="dash-table">
      <thead><tr><th>Ticket ID</th><th>Category</th><th>Description</th><th>Date Filed</th><th>Status</th><th>Action</th></tr></thead>
      <tbody>${list.length ? list.map((r) => `
        <tr>
          <td><strong>#${esc(r.ticketNo)}</strong>${r.emergency ? '<br><span class="badge-pill-custom badge-danger">Emergency</span>' : ''}</td>
          <td>${esc(r.category.name)}</td>
          <td>${esc(r.description)}</td>
          <td>${fmtDate(r.createdAt)}</td>
          <td>${badge(r.status, r.statusLabel)}</td>
          <td>${requestActions(r)}</td>
        </tr>`).join('') : `<tr><td colspan="6">${empty('No complaints yet.')}</td></tr>`}
      </tbody></table></div></div>`);
}

// ---------------------------------------------------------------- 4. AI analysis
async function runAiDiagnostic() {
  const text = document.getElementById('aiInputText').value.trim();
  if (!text) return toast('Please enter a description of the issue.', 'error');
  const resultCard = document.getElementById('aiResultCard');
  resultCard.style.display = '';
  ['aiCat', 'aiSev', 'aiTechCat', 'aiEstCost'].forEach((id) => { document.getElementById(id).innerHTML = '<span class="sk" style="width:80%"></span>'; });
  document.getElementById('aiSummaryText').innerHTML = FC.skeleton('lines', 2);
  document.querySelector('#aiResultCard .ai-header small').innerHTML = '<span class="sk" style="width:120px"></span>';
  try {
    const r = await api.post('/api/ai/diagnose', { description: text });
    document.querySelector('#aiResultCard .ai-header small').textContent = `Confidence Score: ${r.confidence}%`;
    document.getElementById('aiSummaryText').innerHTML = `<strong>Detected Issue:</strong> ${esc(r.detectedIssue)}`
      + (r.tips && r.tips.length ? `<br><small>${r.tips.map(esc).join(' · ')}</small>` : '');
    document.getElementById('aiCat').textContent = r.categoryName;
    const sev = document.getElementById('aiSev');
    sev.textContent = r.severity.charAt(0) + r.severity.slice(1).toLowerCase();
    sev.style.color = { LOW: '#16a34a', MODERATE: '#d97706', HIGH: '#dc2626', CRITICAL: '#991b1b' }[r.severity];
    document.getElementById('aiTechCat').textContent = r.recommendedSpecialist;
    document.getElementById('aiEstCost').textContent = r.estimatedCostLabel;

    const bookBtn = document.querySelector('#aiResultCard .btn-primary-custom');
    bookBtn.onclick = () => {
      switchSection('request');
      document.getElementById('serviceCategory').value = r.categoryCode;
      document.getElementById('complaintDesc').value = text;
      document.getElementById('emergencyPriority').checked = !!r.emergencyRecommended;
    };
    toast('AI analysis complete!', 'success');
  } catch (e) { resultCard.style.display = 'none'; showError(e); }
}

// ---------------------------------------------------------------- 5. technicians
async function loadTechnicians() {
  const section = document.getElementById('section-technicians');
  if (!section.querySelector('.fc-filter-bar')) {
    const cats = await FC.categories();
    setBody('technicians', `
      <div class="fc-filter-bar">
        <select id="techCategory"><option value="">All categories</option>${cats.map((c) => `<option value="${c.code}">${esc(c.name)}</option>`).join('')}</select>
        <select id="techSort"><option value="RATING">Top rated</option><option value="DISTANCE">Nearest</option><option value="EXPERIENCE">Most experienced</option><option value="REVIEWS">Most reviews</option></select>
        <input id="techSearch" type="search" placeholder="Search name, skill or area" />
        <label style="display:flex;align-items:center;gap:6px;font-size:0.85rem;"><input type="checkbox" id="techAvailable" /> Available only</label>
      </div>
      <div class="grid-3-col" id="techGrid"></div>`);
    ['techCategory', 'techSort', 'techAvailable'].forEach((id) => document.getElementById(id).addEventListener('change', renderTechnicians));
    let t;
    document.getElementById('techSearch').addEventListener('input', () => { clearTimeout(t); t = setTimeout(renderTechnicians, 300); });
  }
  await renderTechnicians();
}

async function renderTechnicians() {
  const grid = document.getElementById('techGrid');
  grid.innerHTML = FC.skeleton('techcards', 6);
  const q = new URLSearchParams();
  const cat = document.getElementById('techCategory').value;
  const search = document.getElementById('techSearch').value.trim();
  if (cat) q.set('category', cat);
  if (search) q.set('q', search);
  if (document.getElementById('techAvailable').checked) q.set('availableOnly', 'true');
  q.set('sort', document.getElementById('techSort').value);
  const [list, favs] = await Promise.all([api.get('/api/technicians?' + q), api.get('/api/customer/favourites')]);
  const favIds = new Set(favs.map((f) => f.technician && f.technician.id));
  grid.innerHTML = list.length ? list.map((t) => techCard(t, { favourite: favIds.has(t.id) })).join('') : empty('No technicians match your filters.');
}

async function viewTechnician(id) {
  try {
    const d = await api.get('/api/technicians/' + id);
    const t = d.technician;
    await modal({
      title: `${t.fullName} · ${t.category ? t.category.name : ''}`,
      subtitle: `${t.rating} ★ (${t.reviewCount} reviews) · ${t.experienceYears}+ yrs · ${d.completedJobs} jobs on FixConnect · Area: ${t.serviceArea || '-'}\n`
        + `Skills: ${d.skills || '-'}\n\n`
        + (d.services.length ? 'Services: ' + d.services.map((s) => `${s.title} (${money(s.baseRate)})`).join(', ') + '\n\n' : '')
        + (d.recentReviews.length ? 'Recent reviews:\n' + d.recentReviews.map((r) => `${stars(r.rating)} ${r.customerName}: "${r.comment || ''}"`).join('\n') : 'No reviews yet.'),
      submitText: 'Book Service',
    }).then((ok) => { if (ok) bookTechnician({ id: t.id, fullName: t.fullName, category: t.category ? t.category.code : '' }); });
  } catch (e) { showError(e); }
}

async function toggleFavourite(id, btn) {
  try {
    if (btn.classList.contains('active')) {
      await api.del('/api/customer/favourites/' + id);
      btn.classList.remove('active');
      toast('Removed from favourites');
    } else {
      await api.post('/api/customer/favourites/' + id);
      btn.classList.add('active');
      toast('Saved to favourites', 'success');
    }

  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 6. bookings
async function loadBookings() {
  loading('bookings');
  const list = (await api.get('/api/customer/requests?scope=ALL')).filter((r) => r.bookingNo || r.status === 'PENDING');
  setBody('bookings', `
    <div class="dash-card"><div class="dash-table-container"><table class="dash-table">
      <thead><tr><th>Booking ID</th><th>Service</th><th>Technician</th><th>Date &amp; Time</th><th>Status</th><th>Actions</th></tr></thead>
      <tbody>${list.length ? list.map((r) => `
        <tr>
          <td>#${esc(r.bookingNo || r.ticketNo)}</td>
          <td><strong>${esc(r.title)}</strong></td>
          <td>${esc(r.technician ? r.technician.fullName : 'Matching...')}</td>
          <td>${fmtSlot(r.preferredDate, r.timeSlot)}</td>
          <td>${badge(r.status)}</td>
          <td>${requestActions(r)}</td>
        </tr>`).join('') : `<tr><td colspan="6">${empty('No bookings yet.')}</td></tr>`}
      </tbody></table></div></div>`);
}

async function rescheduleRequest(id) {
  const v = await modal({
    title: 'Reschedule visit',
    fields: [
      { name: 'preferredDate', label: 'New date', type: 'date', required: true, min: new Date().toISOString().slice(0, 10) },
      { name: 'timeSlot', label: 'Time slot', type: 'select', options: Object.entries(FC.SLOT_LABEL).filter(([k]) => k !== 'IMMEDIATE').map(([value, label]) => ({ value, label })) },
    ],
    submitText: 'Reschedule',
  });
  if (!v) return;
  try {
    await api.patch(`/api/customer/requests/${id}/reschedule`, v);
    toast('Visit rescheduled', 'success');
    refreshActive();
  } catch (e) { showError(e); }
}

async function cancelRequest(id) {
  const v = await modal({
    title: 'Cancel this request?', danger: true, submitText: 'Cancel Request',
    fields: [{ name: 'reason', label: 'Reason (optional)', type: 'textarea' }],
  });
  if (!v) return;
  try {
    await api.post(`/api/customer/requests/${id}/cancel`, { reason: v.reason || null });
    toast('Request cancelled');
    refreshActive();
  } catch (e) { showError(e); }
}

async function reviewRequest(id) {
  const v = await modal({
    title: 'Rate your technician',
    fields: [
      { name: 'rating', label: 'Rating', type: 'select', value: '5', options: [5, 4, 3, 2, 1].map((n) => ({ value: n, label: '★'.repeat(n) + ` (${n})` })) },
      { name: 'comment', label: 'Comment', type: 'textarea', placeholder: 'How was the service?' },
    ],
    submitText: 'Submit Review',
  });
  if (!v) return;
  try {
    await api.post(`/api/customer/requests/${id}/review`, { rating: Number(v.rating), comment: v.comment || null });
    toast('Thanks for your review!', 'success');
    refreshActive();
  } catch (e) { showError(e); }
}

function refreshActive() {
  const active = document.querySelector('.dashboard-section.active');
  const id = active ? active.id.replace('section-', '') : 'dashboard';
  if (LOADERS[id]) LOADERS[id]().catch(showError);
  if (id !== 'dashboard') loadDashboard().catch(() => refreshBadges());
}

// ---------------------------------------------------------------- 7. upcoming
async function loadUpcoming() {
  loading('upcoming');
  const list = await api.get('/api/customer/requests?scope=ACTIVE');
  setBody('upcoming', `<div class="grid-2-col">${list.length ? list.map((r) => `
    <div class="dash-card">
      <div class="dash-card-header">${badge(r.status)}<small style="color: var(--slate-400);">ID: #${esc(r.bookingNo || r.ticketNo)}</small></div>
      <h3>${esc(r.title)}</h3>
      <p style="color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.88rem;">Technician: ${r.technician ? `${esc(r.technician.fullName)} (${r.technician.experienceYears}+ Yrs Exp)` : 'Matching a verified technician...'}</p>
      <div style="display: flex; gap: 16px; margin: 14px 0; font-size: 0.85rem;">
        <span><i class="fa-solid fa-calendar"></i> ${fmtDate(r.preferredDate)}</span>
        <span><i class="fa-solid fa-clock"></i> ${esc(r.timeSlotLabel || '')}</span>
      </div>
      ${requestActions(r)}
    </div>`).join('') : `<div class="dash-card">${empty('No upcoming services.')}</div>`}</div>`);
}

// ---------------------------------------------------------------- 8. ongoing
async function loadOngoing() {
  loading('ongoing');
  const list = await api.get('/api/customer/requests?scope=ONGOING');
  setBody('ongoing', list.length ? list.map((r) => {
    const t = r.technician || {};
    return `<div class="active-service-card" style="margin-bottom:16px;"><div class="active-service-content">
      <img src="${FC.techPhoto(t)}" alt="${esc(t.fullName)}" class="active-tech-img" />
      <div class="active-service-info">
        <span class="badge-pill-custom badge-ongoing">${esc(r.statusLabel)}</span>
        <h3>${esc(t.fullName || '')} · ${esc(r.title)}</h3>
        <p>${esc(r.address)}${r.etaMinutes ? ` · ETA ${r.etaMinutes} mins` : ''}</p>
      </div>
      <button class="btn-primary-custom" onclick="openTracking(${r.id})">Open Live Map</button>
    </div></div>`;
  }).join('') : `<div class="dash-card">${empty('No service is in progress right now.')}</div>`);
}

// ---------------------------------------------------------------- 9. live tracking
let trackingRequestId = null;

function openTracking(id) {
  trackingRequestId = id;
  switchSection('tracking');
}

async function pickTrackable() {
  if (trackingRequestId) return trackingRequestId;
  const list = await api.get('/api/customer/requests?scope=ACTIVE');
  const r = list.find((x) => ['EN_ROUTE', 'ARRIVED', 'IN_PROGRESS'].includes(x.status)) || list.find((x) => x.status === 'ACCEPTED');
  return r ? r.id : null;
}

/** Free OpenStreetMap geocoder; returns [lat, lng] or null (never blocks the request for long). */
async function geocodeAddress(text) {
  try {
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), 5000);
    const res = await fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=in&q=' + encodeURIComponent(text), { signal: ctrl.signal });
    clearTimeout(t);
    const list = await res.json();
    return list.length ? [Number(list[0].lat), Number(list[0].lon)] : null;
  } catch (e) { return null; }
}

async function loadTracking() {
  const id = await pickTrackable();
  // Map, moving technician, road route and status sheet live in live-tracking.js
  await window.LiveTracking.open(id);
}

// ---------------------------------------------------------------- 10. ETA / status timeline
const STATUS_ICON = { PENDING: 'fa-paper-plane', ACCEPTED: 'fa-check', EN_ROUTE: 'fa-van-shuttle', ARRIVED: 'fa-location-dot', IN_PROGRESS: 'fa-screwdriver-wrench', COMPLETED: 'fa-circle-check', CANCELLED: 'fa-xmark' };

async function loadEta() {
  loading('eta');
  const id = await pickTrackable() || (state.requests[0] && state.requests[0].id)
    || ((await api.get('/api/customer/requests?scope=ALL'))[0] || {}).id;
  if (!id) return setBody('eta', `<div class="dash-card">${empty('No requests yet.')}</div>`);
  const t = await api.get(`/api/customer/requests/${id}/timeline`);
  setBody('eta', `<div class="dash-card">
    <div class="fc-row" style="margin-bottom:14px;"><strong>Request #${id} · ${esc(t.currentLabel)}</strong>${t.etaMinutes != null ? `<span class="badge-pill-custom badge-ongoing">ETA ${t.etaMinutes} mins</span>` : ''}</div>
    <div style="display: flex; flex-direction: column; gap: 16px;">${t.events.map((e, i) => `
      <div style="display: flex; gap: 14px; align-items: center;">
        <div class="stat-icon-wrapper ${i === t.events.length - 1 && e.status !== 'COMPLETED' ? 'blue' : 'green'}"><i class="fa-solid ${STATUS_ICON[e.status] || 'fa-circle'}"></i></div>
        <div><strong>${esc(e.label)}</strong>
          <p style="margin: 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.8rem;">${esc(e.message || '')} · ${fmtDateTime(e.at)}</p></div>
      </div>`).join('')}</div></div>`);
}

// ---------------------------------------------------------------- 11. favourites
async function loadFavourites() {
  loading('favourites');
  const list = await api.get('/api/customer/favourites');
  setBody('favourites', `<div class="grid-3-col">${list.length
    ? list.map((f) => techCard(f.technician, { favourite: true, note: f.note, bookText: 'Book Again' })).join('')
    : `<div class="dash-card">${empty('No favourites yet - tap the heart on any technician.')}</div>`}</div>`);
}

// ---------------------------------------------------------------- 12. photos
async function loadPhotos() {
  loading('photos');
  const list = await api.get('/api/customer/photos');
  if (!list.length) return setBody('photos', `<div class="dash-card">${empty('No photos yet. Technicians upload before & after photos for each job.')}</div>`);
  const byReq = {};
  list.forEach((p) => { (byReq[p.requestId] = byReq[p.requestId] || []).push(p); });
  setBody('photos', Object.entries(byReq).map(([rid, photos]) => `
    <div class="before-after-card" style="margin-bottom:16px;">
      <h3 style="margin-top: 0; color: var(--fcd-tx-061a36, var(--navy-900));">Request #${rid}</h3>
      <div class="fc-photo-grid">${photos.map((p) => `
        <figure><a href="${FC.fileUrl(p.url)}" target="_blank" rel="noopener"><img src="${FC.fileUrl(p.url)}" alt="${p.type}"></a>
        <figcaption>${p.type} · ${fmtDate(p.uploadedAt)}</figcaption></figure>`).join('')}</div>
    </div>`).join(''));
}

// ---------------------------------------------------------------- 13. invoices
async function loadInvoices() {
  loading('invoices');
  const list = await api.get('/api/customer/invoices');
  setBody('invoices', `<div class="dash-card"><div class="dash-table-container"><table class="dash-table">
    <thead><tr><th>Invoice ID</th><th>Service</th><th>Technician</th><th>Date</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead>
    <tbody>${list.length ? list.map((i) => `
      <tr>
        <td><strong>#${esc(i.invoiceNo)}</strong></td>
        <td>${esc(i.service)}</td>
        <td>${esc(i.technicianName)}</td>
        <td>${fmtDate(i.issuedAt)}</td>
        <td><strong>${money(i.total)}</strong>${Number(i.discount) > 0 ? `<br><small class="text-muted">-${money(i.discount)} off</small>` : ''}</td>
        <td>${badge(i.status)}</td>
        <td><div style="display:flex;gap:6px;flex-wrap:wrap;">
          <button class="btn-secondary-custom btn-sm" onclick="downloadInvoice(${i.id}, '${esc(i.invoiceNo)}')"><i class="fa-solid fa-download"></i> PDF</button>
          ${i.status === 'UNPAID' ? `<button class="btn-primary-custom btn-sm" onclick="payInvoice(${i.id}, '${i.total}')">Pay</button>` : ''}
        </div></td>
      </tr>`).join('') : `<tr><td colspan="7">${empty('No invoices yet.')}</td></tr>`}
    </tbody></table></div></div>`);
}

function downloadInvoice(id, no) {
  FC.download(`/api/invoices/${id}/pdf`, `${no}.pdf`).catch(showError);
}

async function payInvoice(id, total) {
  let coupons = [];
  try { coupons = (await api.get('/api/customer/rewards')).redemptions.filter((r) => !r.used); } catch (e) { /* ignore */ }
  const v = await modal({
    title: 'Pay ' + money(total),
    fields: [
      { name: 'method', label: 'Payment method', type: 'select', options: [{ value: 'UPI', label: 'UPI' }, { value: 'CARD', label: 'Card' }, { value: 'NET_BANKING', label: 'Net Banking' }, { value: 'CASH', label: 'Cash' }] },
      { name: 'couponCode', label: 'Voucher code (optional)', type: 'select', options: [{ value: '', label: 'No voucher' }].concat(coupons.map((c) => ({ value: c.couponCode, label: `${c.couponCode} - ${c.voucherTitle}` }))) },
    ],
    submitText: 'Pay Now',
  });
  if (!v) return;
  try {
    const r = await api.post(`/api/customer/invoices/${id}/pay`, { method: v.method, couponCode: v.couponCode || null });
    toast(`Payment successful! You earned ${r.pointsEarned} points (total ${r.totalPoints}).`, 'success');
    loadInvoices();
    loadDashboard().catch(() => {});
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 14. warranty
async function loadWarranty() {
  loading('warranty');
  const list = await api.get('/api/customer/warranties');
  setBody('warranty', list.length ? list.map((w) => `
    <div class="dash-card" style="margin-bottom:16px;">
      <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 14px;">
        <div>
          ${badge(w.status)}
          <h3 style="margin: 6px 0 2px; color: var(--fcd-tx-061a36, var(--navy-900));">${esc(w.service)} (#${esc(w.warrantyNo)})</h3>
          <p style="margin: 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.85rem;">Technician: ${esc(w.technicianName)} · Valid through ${fmtDate(w.validUntil)}${w.status === 'ACTIVE' ? ` (${w.daysLeft} days left)` : ''}</p>
          ${w.claimNote ? `<p class="fc-muted">Claim: ${esc(w.claimNote)}</p>` : ''}
        </div>
        ${w.status === 'ACTIVE' ? `<button class="btn-primary-custom btn-sm" onclick="claimWarranty(${w.id})">Claim Free Repair</button>` : ''}
      </div>
    </div>`).join('') : `<div class="dash-card">${empty('No warranties yet. Every completed job gets 30 days of cover.')}</div>`);
}

async function claimWarranty(id) {
  const v = await modal({
    title: 'Claim free repair',
    fields: [
      { name: 'issue', label: 'What went wrong?', type: 'textarea', required: true },
      { name: 'preferredDate', label: 'Preferred date', type: 'date', min: new Date().toISOString().slice(0, 10) },
      { name: 'timeSlot', label: 'Time slot', type: 'select', options: Object.entries(FC.SLOT_LABEL).filter(([k]) => k !== 'IMMEDIATE').map(([value, label]) => ({ value, label })) },
    ],
    submitText: 'Submit Claim',
    validate: (x) => FC.textError(x.issue, { label: 'Problem description', min: 10, max: 500 }),
  });
  if (!v) return;
  try {
    const r = await api.post(`/api/customer/warranties/${id}/claim`, { issue: v.issue, preferredDate: v.preferredDate || null, timeSlot: v.timeSlot });
    toast(r.message + ` Ticket #${r.followUpRequest.ticketNo}`, 'success');
    loadWarranty();
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 15. rewards
async function loadRewards() {
  loading('rewards');
  const r = await api.get('/api/customer/rewards');
  setBody('rewards', `
    <div class="grid-2-col" style="margin-bottom:20px;">
      <div class="dash-card" style="background: var(--fcd-bg-f4f8fc, var(--slate-100));">
        <small style="text-transform: uppercase; font-weight: 800; color: var(--fcd-tx-1c6fe5, var(--blue-700));">${esc(r.tier)}</small>
        <h2 style="font-size: 2rem; margin: 4px 0 0; color: var(--fcd-tx-061a36, var(--navy-900));">${r.points} <small style="font-size: 0.9rem;">Points</small></h2>
        <p style="color: var(--fcd-tx-5f6f87, var(--slate-500)); margin-top: 4px; font-size: 0.85rem;">${esc(r.earningRule)}${r.nextTier ? ` ${r.pointsToNextTier} more lifetime points to ${esc(r.nextTier)}.` : ''}</p>
      </div>
      <div class="dash-card">
        <h3>My Voucher Codes</h3>
        ${r.redemptions.length ? r.redemptions.map((x) => `<div class="fc-row" style="padding:6px 0;border-bottom:1px solid var(--fcd-bd-ecf1f8, var(--slate-200));">
          <span><strong>${esc(x.couponCode)}</strong><br><small class="text-muted">${esc(x.voucherTitle)}</small></span>
          ${x.used ? badge('CANCELLED', 'Used') : badge('ACTIVE', 'Available')}</div>`).join('') : empty('Redeem points to get voucher codes; apply them when paying an invoice.')}
      </div>
    </div>
    <div class="grid-3-col">${r.vouchers.map((v) => `
      <div class="dash-card">
        <h3>${esc(v.title)}</h3>
        <p style="color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.85rem;">${esc(v.description || '')}</p>
        <button class="btn-primary-custom" ${v.affordable ? '' : 'disabled style="opacity:.55;cursor:not-allowed;"'} onclick="redeemVoucher(${v.id})">Redeem ${v.pointsCost} Points</button>
      </div>`).join('')}</div>`);
}

async function redeemVoucher(id) {
  try {
    const r = await api.post('/api/customer/rewards/redeem', { voucherId: id });
    toast(r.message, 'success');
    loadRewards();
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 16. notifications
async function loadNotifications() {
  loading('notifications');
  const r = await api.get('/api/notifications');
  setBadge('notifications', r.unreadCount);
  setBody('notifications', `<div class="dash-card">
    <div class="fc-row" style="margin-bottom:12px;"><span class="fc-muted">${r.unreadCount} unread</span>
      ${r.unreadCount ? '<button class="btn-secondary-custom btn-sm" onclick="markAllRead()">Mark all read</button>' : ''}</div>
    <div style="display: flex; flex-direction: column; gap: 14px;">${r.notifications.length ? r.notifications.map((n) => `
      <div style="padding-bottom: 10px; border-bottom: 1px solid var(--fcd-bd-ecf1f8, var(--slate-200)); ${n.read ? 'opacity:.7;' : ''}" class="fc-row">
        <div><strong style="color: var(--fcd-tx-061a36, var(--navy-900));">${n.read ? '' : '● '}${esc(n.title)}</strong>
          <p style="margin: 2px 0 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.85rem;">${esc(n.message)}</p>
          <small style="color: var(--slate-400);">${fmtDateTime(n.createdAt)}</small></div>
        <div style="display:flex;gap:6px;">${n.read ? '' : `<button class="btn-secondary-custom btn-sm" onclick="markRead(${n.id})">Mark read</button>`}
          <button class="btn-secondary-custom btn-sm" title="Delete" onclick="deleteNotification(${n.id})"><i class="fa-solid fa-trash"></i></button></div>
      </div>`).join('') : empty('You are all caught up!')}</div></div>`);
}
async function markRead(id) { try { await api.patch(`/api/notifications/${id}/read`); loadNotifications(); } catch (e) { showError(e); } }
async function markAllRead() { try { await api.patch('/api/notifications/read-all'); loadNotifications(); } catch (e) { showError(e); } }
async function deleteNotification(id) { try { await api.del(`/api/notifications/${id}`); loadNotifications(); } catch (e) { showError(e); } }

// ---------------------------------------------------------------- 17. history
async function loadHistory() {
  loading('history');
  const list = await api.get('/api/customer/history');
  setBody('history', `<div class="dash-card"><div class="dash-table-container"><table class="dash-table">
    <thead><tr><th>Date</th><th>Service</th><th>Technician</th><th>Cost</th><th>Rating</th></tr></thead>
    <tbody>${list.length ? list.map((h) => `
      <tr><td>${fmtDate(h.date)}</td><td>${esc(h.service)}</td><td>${esc(h.technician || '-')}</td><td>${money(h.cost)}</td>
        <td>${h.rating ? `<span class="stars">${stars(h.rating)}</span> ${h.rating.toFixed(1)}` : `<button class="btn-secondary-custom btn-sm" onclick="reviewRequest(${h.requestId})">Rate</button>`}</td></tr>`).join('')
      : `<tr><td colspan="5">${empty('No completed services yet.')}</td></tr>`}
    </tbody></table></div></div>`);
}

// ---------------------------------------------------------------- 18. profile (inline section)
async function loadProfileSection() {
  const p = await api.get('/api/customer/profile');
  setBody('profile', `<div class="dash-card" style="max-width: 760px;">
    <div id="dashPhotoBox" style="display:flex;flex-direction:column;align-items:flex-start;margin-bottom:18px;padding-bottom:16px;border-bottom:1px solid var(--fcd-bd-ecf1f8, var(--slate-200));">
      <img class="fc-my-avatar" id="dashPhoto" src="${FC.avatarSrc(p.fullName, (FC.getUser() || {}).photoUrl)}" alt="${esc(p.fullName)}" style="width:80px;height:80px;border-radius:50%;object-fit:cover;border:2px solid var(--fcd-bd-ecf1f8, var(--slate-200));" />
    </div>
    <form id="dashProfileForm">
      <div class="grid-2-col">
        <div class="form-group"><label>Full Name</label><input name="fullName" class="form-control-custom" value="${esc(p.fullName)}" required /></div>
        <div class="form-group"><label>Phone Number</label><input name="phone" type="tel" class="form-control-custom" value="${esc(p.phone || '')}" required /></div>
      </div>
      <div class="form-group"><label>Email Address</label><input name="email" type="email" class="form-control-custom" value="${esc(p.email)}" required /></div>
      <div class="form-group"><label>Primary Service Address</label><input name="primaryAddress" class="form-control-custom" value="${esc(p.primaryAddress || '')}" required /></div>
      <div style="display: flex; gap: 12px; margin-top: 16px;">
        <button type="submit" class="btn-primary-custom">Save Profile</button>
        <a href="customer-profile.html" class="btn-secondary-custom">Full profile page</a>
      </div>
    </form></div>`);
  FC.mountPhotoControls(document.getElementById('dashPhoto'), document.getElementById('dashPhotoBox'));
  document.getElementById('dashProfileForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    Object.keys(f).forEach((k) => { if (typeof f[k] === 'string') f[k] = f[k].trim(); });
    const err = FC.firstError(
      FC.nameError(f.fullName, 'Full name'),
      FC.phoneError(f.phone),
      FC.emailError(f.email),
      FC.addressError(f.primaryAddress, { label: 'Primary service address' }));
    if (err) return toast(err, 'error');
    try {
      await api.put('/api/customer/profile', Object.assign({ altPhone: p.altPhone || '' }, f));
      toast('Profile details updated successfully!', 'success');
      loadDashboard();
    } catch (err) { showError(err); }
  });
}

// ---------------------------------------------------------------- 19. settings
async function loadSettings() {
  const s = await api.get('/api/customer/settings');
  const row = (key, title, desc, first) => `
    <div style="display: flex; align-items: center; justify-content: space-between; ${first ? '' : 'border-top: 1px solid var(--fcd-bd-ecf1f8, var(--slate-200)); padding-top: 14px;'}">
      <div><strong>${title}</strong><p style="margin: 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.82rem;">${desc}</p></div>
      <label class="switch-custom"><input type="checkbox" data-setting="${key}" ${s[key] ? 'checked' : ''} /><span class="slider-custom"></span></label>
    </div>`;
  setBody('settings', `<div class="dash-card" style="max-width: 760px;"><div style="display: flex; flex-direction: column; gap: 16px;">
    ${row('smsAlerts', 'SMS Emergency Alerts', 'Direct SMS alerts for SOS dispatches.', true)}
    ${row('whatsappUpdates', 'WhatsApp Status Updates', 'Live tracking updates sent to WhatsApp.')}
    ${row('emailNotifications', 'E-mail Notifications', 'Invoices, warranty and booking updates by e-mail.')}
  </div></div>`);
  document.querySelectorAll('#section-settings input[data-setting]').forEach((cb) => {
    cb.addEventListener('change', async () => {
      try {
        await api.put('/api/customer/settings', { [cb.dataset.setting]: cb.checked });
        toast('Preference saved', 'success');
      } catch (e) { cb.checked = !cb.checked; showError(e); }
    });
  });
}

// ---------------------------------------------------------------- 20. emergency SOS
const SOS_TYPES = [
  ['GENERAL', 'General Emergency SOS'], ['WATER_LEAKAGE', 'Water Leakage / Pipe Burst'],
  ['ELECTRICAL_FAULT', 'Electrical Short Circuit / Fault'], ['AC_GAS_LEAK', 'AC Breakdown / Gas Leakage'],
  ['DOOR_LOCK', 'Door Lock Jam / Key Failure'],
];
const SOS_FROM_LABEL = { 'Emergency SOS': 'GENERAL', 'Water Leakage': 'WATER_LEAKAGE', 'Electrical Emergency': 'ELECTRICAL_FAULT', 'AC Emergency': 'AC_GAS_LEAK', 'Locksmith Emergency': 'DOOR_LOCK' };

async function loadEmergency() {
  const card = document.querySelector('#section-emergency .dash-card');
  if (card && !document.getElementById('sosType')) {
    const primary = (state.addresses.find((a) => a.primary) || {}).fullAddress || '';
    card.querySelector('p').insertAdjacentHTML('afterend', `
      <div class="grid-2-col" style="margin-top:14px;">
        <div class="form-group"><label>Emergency type</label>
          <select id="sosType" class="form-control-custom">${SOS_TYPES.map(([v, l]) => `<option value="${v}">${l}</option>`).join('')}</select></div>
        <div class="form-group"><label>Location</label><input id="sosLocationDash" class="form-control-custom" value="${esc(FC.read('emergencyLocation') || primary)}" /></div>
      </div>
      <div class="form-group"><label>What is happening?</label><textarea id="sosDescDash" class="form-control-custom" rows="2" placeholder="e.g. Main water pipe burst in kitchen">${esc(FC.read('emergencyDescription') || '')}</textarea></div>`);
    try {
      const info = await api.get('/api/emergency/categories', { auth: false });
      const tel = card.querySelector('a[href^="tel:"]');
      if (tel) tel.href = 'tel:' + info.hotline.replace(/\s+/g, '');
    } catch (e) { /* ignore */ }
  }
  const pending = FC.read('emergencyType');
  if (pending) {
    document.getElementById('sosType').value = SOS_FROM_LABEL[pending] || 'GENERAL';
    toast('Confirm your emergency below and press "Dispatch SOS Tech".', 'info');
  }
}

async function triggerSosAlert() {
  const ok = await FC.confirmDialog('Send an emergency SOS?',
    'This broadcasts an immediate HIGH PRIORITY alert to all available verified technicians nearby.', 'Send SOS', true);
  if (!ok) return;
  try {
    const here = await FC.currentPosition(8000);   // where the emergency is (customer's device)
    const r = await api.post('/api/customer/emergency/sos', {
      latitude: here ? here[0] : null,
      longitude: here ? here[1] : null,
      type: (document.getElementById('sosType') || {}).value || 'GENERAL',
      location: ((document.getElementById('sosLocationDash') || {}).value || '').trim() || null,
      description: ((document.getElementById('sosDescDash') || {}).value || '').trim() || null,
    });
    ['emergencyType', 'emergencyLocation', 'emergencyDescription'].forEach((k) => FC.store(k, null));
    toast(`🚨 ${r.message}\n${r.techniciansNotified} technician(s) notified. Hotline: ${r.hotline}`, 'success');
    loadDashboard().catch(() => {});
    switchSection('complaints');
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- section loaders
const LOADERS = {
  dashboard: loadDashboard,
  complaints: loadComplaints,
  technicians: loadTechnicians,
  upcoming: loadUpcoming,
  ongoing: loadOngoing,
  tracking: loadTracking,
  eta: loadEta,
  photos: loadPhotos,
  invoices: loadInvoices,
  notifications: loadNotifications,
  history: loadHistory,
  profile: loadProfileSection,
  settings: loadSettings,
  emergency: loadEmergency,
};
