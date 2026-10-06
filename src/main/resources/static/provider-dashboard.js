/* ==========================================================================
   FIXCONNECT AI - SERVICE PROVIDER DASHBOARD (connected to the Spring Boot API)
   Each sidebar module loads live data from /api/provider/** when opened.
   Requires api.js (window.FixConnect).
   ========================================================================== */

const FC = window.FixConnect;
const { api, esc, money, fmtDate, fmtDateTime, fmtSlot, timeAgo, stars, badge, toast, showError, modal } = FC;

const ME = FC.requireAuth('PROVIDER');

const state = { profile: null, jobs: [], selectedJobId: null, offerings: {} };

const NEXT_STEP = {
  ACCEPTED: { status: 'EN_ROUTE', label: 'Start Travel (En Route)', icon: 'fa-van-shuttle' },
  EN_ROUTE: { status: 'ARRIVED', label: 'Arrived at Location', icon: 'fa-location-dot' },
  ARRIVED: { status: 'IN_PROGRESS', label: 'Start Repair Work', icon: 'fa-screwdriver-wrench' },
  IN_PROGRESS: { status: 'COMPLETED', label: 'Mark Completed', icon: 'fa-circle-check' },
};
const ACTIVE = ['ACCEPTED', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS'];

// ---------------------------------------------------------------- bootstrap
document.addEventListener('DOMContentLoaded', () => {
  if (!ME) return;
  initSidebarNavigation();
  initMobileDrawer();
  loadDashboard().catch((e) => { showError(e); FC.stopSkeletons(document); });
  syncLiveShare();
  setInterval(syncLiveShare, 30000);
  const hash = location.hash.replace('#section-', '').replace('#', '');
  if (hash && document.getElementById('section-' + hash)) switchSection(hash);
});

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
  const link = document.querySelector(`.sidebar-link[data-section="${sectionId}"]`);
  if (link) link.classList.add('active');
  const section = document.getElementById(`section-${sectionId}`);
  if (section) {
    section.classList.add('active');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  const sidebar = document.getElementById('sidebar');
  if (sidebar) sidebar.classList.remove('mobile-open');
  if (history.replaceState) history.replaceState(null, '', '#section-' + sectionId);
  const loader = LOADERS[sectionId];
  if (loader) loader().catch((e) => { showError(e); failSection(sectionId); });
}

function initMobileDrawer() {
  const toggleBtn = document.getElementById('sidebarToggle');
  const sidebar = document.getElementById('sidebar');
  if (toggleBtn && sidebar) toggleBtn.addEventListener('click', () => sidebar.classList.toggle('mobile-open'));
}

// ---------------------------------------------------------------- helpers
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
const SK_KIND = { requests: ['list', 3], 'my-services': ['cards', 3], 'ai-complaint': ['form', 2], 'ongoing-services': ['list', 2],
  'eta-status': ['list', 2], 'provider-photos': ['cards', 3], ratings: ['list', 4], 'provider-notifications': ['list', 5],
  'provider-profile': ['form', 7], 'provider-settings': ['form', 6] };
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

function refreshActive() {
  const active = document.querySelector('.dashboard-section.active');
  const id = active ? active.id.replace('section-', '') : 'dashboard';
  if (LOADERS[id]) LOADERS[id]().catch(showError);
  if (id !== 'dashboard') loadDashboard().catch(() => refreshBadges());
  syncLiveShare();
}

function priorityBadge(r) {
  if (r.emergency) return '<span class="badge-pill-custom badge-danger">Urgent SOS</span>';
  if (r.severity === 'HIGH' || r.severity === 'CRITICAL') return '<span class="badge-pill-custom badge-warning">High Priority</span>';
  return '<span class="badge-pill-custom badge-info">Standard Job</span>';
}

function requestCard(r, compact) {
  const c = r.customer || {};
  return `
    <div class="${compact ? 'fc-list-item' : 'dash-card'}" id="req-${r.id}">
      <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 14px;">
        <div style="flex:1;min-width:220px;">
          ${priorityBadge(r)} ${r.directBooking ? '<span class="badge-pill-custom badge-verified">Booked you directly</span>' : ''}
          ${r.warrantyClaim ? '<span class="badge-pill-custom badge-scheduled">Warranty claim</span>' : ''}
          <h3 style="margin: 6px 0 4px; color: var(--fcd-tx-061a36, var(--navy-900)); font-size:${compact ? '1rem' : '1.1rem'};">${esc(r.title)}</h3>
          <p style="margin: 0 0 4px; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.86rem;">${esc(r.description)}</p>
          <p style="margin: 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.85rem;">
            Customer: <strong>${esc(c.fullName || '')}</strong> · ${esc(r.address)}${r.distanceKm != null ? ` (${r.distanceKm} km away)` : ''}<br>
            <i class="fa-solid fa-calendar"></i> ${fmtSlot(r.preferredDate, r.timeSlot)} · <small>${timeAgo(r.createdAt)}</small>
          </p>
        </div>
        <div style="display: flex; gap: 8px;">
          <button class="btn-primary-custom ${compact ? 'btn-sm' : ''}" onclick="acceptRequest(${r.id}, this)">Accept Request</button>
          <button class="btn-secondary-custom ${compact ? 'btn-sm' : ''}" onclick="rejectRequest(${r.id}, this)">Decline</button>
        </div>
      </div>
    </div>`;
}

function jobRow(r, actions) {
  const c = r.customer || {};
  return `<tr>
    <td><strong>${fmtSlot(r.preferredDate, r.timeSlot)}</strong><br><small class="text-muted">#${esc(r.bookingNo || r.ticketNo)}</small></td>
    <td>${esc(c.fullName || '')}<br><small class="text-muted">${esc(r.address)}</small></td>
    <td>${esc(r.title)}</td>
    <td>${badge(r.status)}</td>
    <td>${actions !== undefined ? actions : jobActions(r)}</td>
  </tr>`;
}

function jobActions(r) {
  const a = [];
  const next = NEXT_STEP[r.status];
  if (next) a.push(`<button class="btn-primary-custom btn-sm" onclick="advanceJob(${r.id}, '${next.status}')"><i class="fa-solid ${next.icon}"></i> ${next.label}</button>`);
  if (ACTIVE.includes(r.status)) a.push(`<button class="btn-secondary-custom btn-sm" onclick="updateEta(${r.id})">Update ETA</button>`);
  if ((r.status === 'IN_PROGRESS' || r.status === 'COMPLETED') && !r.invoiceId) a.push(`<button class="btn-secondary-custom btn-sm" onclick="createInvoice(${r.id})">Invoice</button>`);
  if (r.status !== 'CANCELLED') a.push(`<button class="btn-secondary-custom btn-sm" onclick="openPhotos(${r.id})"><i class="fa-solid fa-camera"></i></button>`);
  if (r.customer && r.customer.phone) a.push(`<a class="btn-secondary-custom btn-sm" href="tel:${esc(r.customer.phone.replace(/\s+/g, ''))}"><i class="fa-solid fa-phone"></i></a>`);
  return `<div style="display:flex;gap:6px;flex-wrap:wrap;">${a.join('')}</div>`;
}

function table(headers, rows, emptyText) {
  return `<div class="dash-card"><div class="dash-table-container"><table class="dash-table">
    <thead><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr></thead>
    <tbody>${rows.length ? rows.join('') : `<tr><td colspan="${headers.length}">${empty(emptyText)}</td></tr>`}</tbody>
  </table></div></div>`;
}

async function fetchJobs(scope) {
  return api.get('/api/provider/jobs?scope=' + scope);
}

// ---------------------------------------------------------------- 1. dashboard
async function loadDashboard() {
  const d = await api.get('/api/provider/dashboard');
  const s = d.stats;
  applyBadges(s);
  document.querySelectorAll('.sidebar-user-name').forEach((el) => { el.textContent = d.fullName; });

  const hour = new Date().getHours();
  const greet = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const header = document.querySelector('#section-dashboard .section-top-header');
  header.querySelector('h1').textContent = `${greet}, ${d.fullName} 👋`;
  header.querySelector('p').textContent = `${d.headline || 'FixConnect Technician'} · Service dispatches and daily schedule.`;
  const reqBtn = header.querySelector('.section-actions button');
  if (reqBtn) reqBtn.innerHTML = `<i class="fa-solid fa-inbox"></i> View Requests (${s.newRequests})`;

  const cards = document.querySelectorAll('#section-dashboard .stats-grid-4 .stat-card-modern');
  const data = [
    [String(s.newRequests).padStart(2, '0'), `${s.highPriority} high priority`],
    [String(s.todaysJobs).padStart(2, '0'), `${s.ongoing} ongoing visit${s.ongoing === 1 ? '' : 's'}`],
    [String(s.completedJobs), `${s.completionRate}% completion rate`],
    [s.rating.toFixed(1), `${s.reviewCount} reviews`],
  ];
  cards.forEach((c, i) => {
    c.querySelector('strong').textContent = data[i][0];
    c.querySelector('.stat-subtext').textContent = data[i][1];
  });

  const avatar = document.querySelector('#section-dashboard .active-tech-img');
  if (avatar) {
    avatar.src = FC.avatarSrc(d.fullName, (FC.getUser() || {}).photoUrl);
    avatar.alt = d.fullName;
    avatar.classList.add('fc-my-avatar');
    avatar.classList.remove('sk-bg');
  }
  const info = document.querySelector('#section-dashboard .active-service-info');
  if (info) {
    info.innerHTML = `
      ${d.verified ? '<span class="badge-pill-custom badge-verified"><i class="fa-solid fa-circle-check"></i> Verified FixConnect Pro</span>'
        : '<span class="badge-pill-custom badge-warning">Verification pending</span>'}
      <h3>${esc(d.fullName)} · ${esc(d.headline || '')}</h3>
      <p>Active Service Zone: ${esc(d.serviceArea || '-')} (${d.serviceRadiusKm} km radius).</p>
      <div class="active-service-meta">
        <span><i class="fa-solid fa-star" style="color: var(--fcd-tx-f59e0b, #f59e0b);"></i> ${s.rating.toFixed(1)} (${s.reviewCount} reviews)</span>
        <span><i class="fa-solid fa-award"></i> ${s.acceptanceRate}% Acceptance</span>
        <span><i class="fa-solid fa-circle-check"></i> ${s.completedJobs} jobs done</span>
      </div>`;
  }

  // Availability toggle in the sidebar
  const toggle = document.getElementById('availabilityToggle');
  if (toggle) { toggle.checked = d.available; paintAvailability(d.available); }

  // Incoming quick list (first dash-card in the left column)
  const leftCol = document.querySelector('#section-dashboard .grid-main-side > div');
  const incomingCard = leftCol && leftCol.querySelector('.dash-card');
  if (incomingCard) {
    const header2 = incomingCard.querySelector('.dash-card-header');
    header2.querySelector('a').textContent = `View All (${s.newRequests})`;
    [...incomingCard.children].forEach((c) => { if (c !== header2) c.remove(); });
    incomingCard.insertAdjacentHTML('beforeend', d.incomingRequests.length
      ? d.incomingRequests.map((r) => requestCard(r, true)).join('') : empty('No new requests right now.'));
  }

  // Today's schedule table
  const tbody = document.querySelector('#section-dashboard .dash-table tbody');
  if (tbody) {
    tbody.innerHTML = d.todaySchedule.length ? d.todaySchedule.map((r) => jobRow(r,
      NEXT_STEP[r.status]
        ? `<button class="btn-primary-custom btn-sm" onclick="advanceJob(${r.id}, '${NEXT_STEP[r.status].status}')">${NEXT_STEP[r.status].label}</button>`
        : badge(r.status))).join('')
      : `<tr><td colspan="5">${empty('No jobs scheduled for today.')}</td></tr>`;
  }

  // Performance bars
  const perf = [...document.querySelectorAll('#section-dashboard .dash-card')].find((c) => c.textContent.includes('Acceptance Rate'));
  if (perf) {
    const bars = perf.querySelectorAll('div[style*="height: 100%"]');
    const labels = perf.querySelectorAll('div[style*="justify-content: space-between"] > span:last-child');
    [s.acceptanceRate, s.completionRate].forEach((v, i) => {
      if (bars[i]) bars[i].style.width = v + '%';
      if (labels[i]) labels[i].textContent = v + '%';
    });
  }

  // Feedback
  const fb = [...document.querySelectorAll('#section-dashboard .dash-card')].find((c) => c.querySelector('.dash-card-title')?.textContent.includes('Feedback'));
  if (fb) {
    const h = fb.querySelector('.dash-card-header');
    [...fb.children].forEach((c) => { if (c !== h) c.remove(); });
    fb.insertAdjacentHTML('beforeend', d.recentReviews.length ? d.recentReviews.map((r) => `
      <div style="background: var(--fcd-bg-f8fafc, var(--slate-50)); padding: 12px; border-radius: var(--radius-md); margin-bottom:8px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px;">
          <strong style="color: var(--fcd-tx-061a36, var(--navy-900)); font-size: 0.88rem;">${esc(r.customerName)}</strong>
          <span class="stars">${stars(r.rating)}</span>
        </div>
        <p style="margin: 0; font-size: 0.82rem; color: var(--fcd-tx-5f6f87, var(--slate-500));">"${esc(r.comment || '')}"</p>
        <small style="color: var(--slate-400); display: block; margin-top: 4px;">${timeAgo(r.createdAt)}</small>
      </div>`).join('') : empty('No reviews yet.'));
  }

}

/** Sidebar count comes only from the server: unread notifications. */
function applyBadges(s) {
  setBadge('provider-notifications', s.unreadNotifications || 0);
}
async function refreshBadges() {
  try { applyBadges((await api.get('/api/provider/dashboard')).stats); } catch (e) { /* keep last values */ }
}
setInterval(() => { if (ME && document.visibilityState === 'visible') refreshBadges(); }, 60000);

// ---------------------------------------------------------------- availability
function paintAvailability(on) {
  const statusText = document.getElementById('sidebarAvailabilityStatus');
  const dot = document.querySelector('.sidebar-status-card .status-dot');
  if (statusText) statusText.textContent = on ? 'Available for Work' : 'Offline / Busy';
  if (dot) {
    dot.style.background = on ? 'var(--green-600)' : 'var(--slate-400)';
    dot.style.boxShadow = on ? '0 0 0 3px rgba(22, 163, 74, 0.2)' : 'none';
  }
}

async function toggleAvailability(input) {
  try {
    const r = await api.patch('/api/provider/availability', { available: input.checked });
    paintAvailability(r.available);
    toast(r.message, r.available ? 'success' : 'info');
  } catch (e) {
    input.checked = !input.checked;
    showError(e);
  }
}

// ---------------------------------------------------------------- 2. requests
async function loadRequests() {
  loading('requests');
  const list = await api.get('/api/provider/requests');
  setBody('requests', `<div style="display: flex; flex-direction: column; gap: 16px;" id="requestsListContainer">
    ${list.length ? list.map((r) => requestCard(r)).join('') : `<div class="dash-card">${empty('No incoming requests. Make sure you are set to "Available for Work".')}</div>`}
  </div>`);
}

async function acceptRequest(id, btn) {
  try {
    const r = await FC.busy(btn, () => api.post(`/api/provider/requests/${id}/accept`));
    toast(`✅ Request accepted! Booking #${r.bookingNo}\n${r.customer.fullName} has been notified.`, 'success');
    state.selectedJobId = r.id;
    loadDashboard().catch(() => {});
    switchSection('ongoing-services');
  } catch (e) { showError(e); }
}

async function rejectRequest(id, btn) {
  const v = await modal({ title: 'Decline this request?', fields: [{ name: 'reason', label: 'Reason (optional)', type: 'textarea' }], submitText: 'Decline', danger: true });
  if (!v) return;
  try {
    await api.post(`/api/provider/requests/${id}/decline`, { reason: v.reason || null });
    const card = document.getElementById('req-' + id);
    if (card) card.remove();
    toast('Request declined');
    loadDashboard().catch(() => {});
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 3. services & rates
async function loadServices() {
  loading('my-services');
  const list = await api.get('/api/provider/services');
  list.forEach((o) => { state.offerings[o.id] = o; });
  setBody('my-services', `
    <div style="margin-bottom:16px;"><button class="btn-primary-custom" onclick="editService()"><i class="fa-solid fa-plus"></i> Add Service</button></div>
    <div class="grid-2-col">${list.length ? list.map((o) => `
      <div class="dash-card" style="${o.active ? '' : 'opacity:.6;'}">
        <div class="fc-row"><h3 style="margin:0;"><i class="fa-solid fa-screwdriver-wrench" style="color: var(--fcd-tx-1c6fe5, var(--blue-700));"></i> ${esc(o.title)}</h3>
          ${o.active ? '' : '<span class="badge-pill-custom badge-cancelled">Inactive</span>'}</div>
        <p style="color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.85rem;">${esc(o.description || '')}</p>
        <div style="margin-top: 12px; font-weight: 700; color: var(--fcd-tx-061a36, var(--navy-900));">Base Rate: ${money(o.baseRate)} / Visit</div>
        <div style="display:flex;gap:8px;margin-top:12px;">
          <button class="btn-secondary-custom btn-sm" onclick="editService(state.offerings[${o.id}])">Edit</button>
          <button class="btn-secondary-custom btn-sm" onclick="deleteService(${o.id})"><i class="fa-solid fa-trash"></i></button>
        </div>
      </div>`).join('') : `<div class="dash-card">${empty('Add the services you offer and your base rates.')}</div>`}</div>`);
}

async function editService(o) {
  o = o || {};
  const v = await modal({
    title: o.id ? 'Edit service' : 'Add service',
    fields: [
      { name: 'title', label: 'Service title', required: true, value: o.title },
      { name: 'description', label: 'Description', type: 'textarea', value: o.description },
      { name: 'baseRate', label: 'Base rate (₹ per visit)', type: 'number', min: 1, required: true, value: o.baseRate },
      { name: 'active', label: 'Status', type: 'select', value: String(o.active !== false), options: [{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }] },
    ],
    validate: (x) => FC.firstError(
      FC.textError(x.title, { label: 'Service title', min: 3, max: 120 }),
      FC.textError(x.description, { label: 'Description', max: 500, optional: true }),
      FC.numberError(x.baseRate, { label: 'Base rate', min: 1, max: 100000 })),
  });
  if (!v) return;
  const body = { title: v.title, description: v.description || null, baseRate: Number(v.baseRate), active: v.active === 'true' };
  try {
    if (o.id) await api.put('/api/provider/services/' + o.id, body);
    else await api.post('/api/provider/services', body);
    toast('Service saved', 'success');
    loadServices();
  } catch (e) { showError(e); }
}

async function deleteService(id) {
  if (!(await FC.confirmDialog('Delete this service?', '', 'Delete', true))) return;
  try { await api.del('/api/provider/services/' + id); loadServices(); } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 4. AI repair guide
async function loadAiGuide() {
  const section = document.getElementById('section-ai-complaint');
  if (section.querySelector('#aiGuideText')) return;
  const jobs = (await fetchJobs('ALL')).filter((j) => ACTIVE.includes(j.status));
  setBody('ai-complaint', `<div class="dash-card" style="max-width: 760px;">
      ${jobs.length ? `<div class="form-group"><label>Load from one of my jobs</label>
        <select id="aiGuideJob" class="form-control-custom"><option value="">-- type manually --</option>
        ${jobs.map((j) => `<option value="${j.id}" data-desc="${esc(j.description)}" data-cat="${j.category.code}">#${esc(j.bookingNo)} · ${esc(j.title)}</option>`).join('')}</select></div>` : ''}
      <div class="form-group"><label>Customer Complaint Description</label>
        <textarea id="aiGuideText" class="form-control-custom">AC compressor turns off after 5 minutes and shows Error E4.</textarea></div>
      <button class="btn-primary-custom" onclick="generateGuide(this)"><i class="fa-solid fa-brain"></i> Generate Repair Guide</button>
    </div>
    <div id="aiGuideResult" style="margin-top:20px;max-width:760px;"></div>`);
  const sel = document.getElementById('aiGuideJob');
  if (sel) sel.addEventListener('change', () => {
    const o = sel.selectedOptions[0];
    if (o && o.dataset.desc) document.getElementById('aiGuideText').value = o.dataset.desc;
  });
}

async function generateGuide(btn) {
  const description = document.getElementById('aiGuideText').value.trim();
  const sel = document.getElementById('aiGuideJob');
  const categoryCode = sel && sel.selectedOptions[0] ? sel.selectedOptions[0].dataset.cat : null;
  try {
    const g = await FC.busy(btn, () => api.post('/api/ai/repair-guide', { description, categoryCode: categoryCode || null }));
    const list = (items) => `<ul style="margin:6px 0 0;padding-left:18px;">${items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul>`;
    document.getElementById('aiGuideResult').innerHTML = `
      <div class="ai-box-container">
        <div class="ai-header"><div class="ai-icon-sparkle"><i class="fa-solid fa-robot"></i></div>
          <div><h3 style="margin: 0; color: var(--fcd-tx-061a36, var(--navy-900));">AI Repair Guide</h3>
          <small style="color: var(--fcd-tx-1c6fe5, var(--blue-700)); font-weight: 700;">Confidence ${g.confidence}% · ~${g.estimatedDurationMinutes} mins</small></div></div>
        <p><strong>Probable cause:</strong> ${esc(g.probableCause)}</p>
        <div class="grid-2-col">
          <div><strong><i class="fa-solid fa-toolbox"></i> Required tools</strong>${list(g.requiredTools)}</div>
          <div><strong><i class="fa-solid fa-list-check"></i> Diagnostic steps</strong><ol style="margin:6px 0 0;padding-left:18px;">${g.diagnosticSteps.map((i) => `<li>${esc(i)}</li>`).join('')}</ol></div>
        </div>
        <div style="margin-top:12px;"><strong style="color:var(--fcd-tx-b91c1c, #b91c1c);"><i class="fa-solid fa-helmet-safety"></i> Safety</strong>${list(g.safetyNotes)}</div>
      </div>`;
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 5-8. schedule / upcoming / ongoing / completed
async function loadSchedule() {
  loading('schedule');
  const list = await fetchJobs('TODAY');
  setBody('schedule', list.length ? list.map((r) => `
    <div class="dash-card" style="margin-bottom:12px;">
      <div style="display: flex; gap: 16px; align-items: center; flex-wrap: wrap;">
        <div style="font-weight: 800; color: var(--fcd-tx-1c6fe5, var(--blue-700)); font-size: 1.05rem; width: 110px;">${esc(FC.SLOT_TIME[r.timeSlot] || '')}</div>
        <div style="flex: 1; min-width: 200px;">
          <strong>${esc(r.customer.fullName)} · ${esc(r.title)}</strong>
          <p style="margin: 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.82rem;">${esc(r.address)}</p>
        </div>
        ${badge(r.status)}
        ${jobActions(r)}
      </div>
    </div>`).join('') : `<div class="dash-card">${empty('No jobs scheduled for today.')}</div>`);
}

async function loadUpcomingBookings() {
  loading('upcoming-bookings');
  const list = await fetchJobs('UPCOMING');
  setBody('upcoming-bookings', table(['Date & Slot', 'Customer & Address', 'Service', 'Status', 'Action'],
    list.map((r) => jobRow(r)), 'No upcoming visits scheduled yet.'));
}

async function loadOngoing() {
  loading('ongoing-services');
  const list = (await fetchJobs('ALL')).filter((j) => ACTIVE.includes(j.status));
  state.jobs = list;
  setBody('ongoing-services', list.length ? list.map((r) => `
    <div class="active-service-card" style="margin-bottom:16px;${state.selectedJobId === r.id ? 'outline:2px solid var(--blue-700);' : ''}">
      <div class="active-service-content">
        <div class="active-service-info">
          ${badge(r.status, `${r.statusLabel} · #${r.bookingNo}`)}
          <h3>${esc(r.customer.fullName)} · ${esc(r.title)}</h3>
          <p>Location: ${esc(r.address)}${r.etaMinutes ? ` · ETA ${r.etaMinutes} mins` : ''}</p>
          <div class="active-service-meta">
            <span><i class="fa-solid fa-calendar"></i> ${fmtSlot(r.preferredDate, r.timeSlot)}</span>
            ${r.customer.phone ? `<span><i class="fa-solid fa-phone"></i> ${esc(r.customer.phone)}</span>` : ''}
            ${r.latitude != null ? `<a target="_blank" rel="noopener" href="https://www.google.com/maps/dir/?api=1&destination=${r.latitude},${r.longitude}"><i class="fa-solid fa-map"></i> Navigate</a>` : ''}
          </div>
        </div>
        ${jobActions(r)}
      </div>
    </div>`).join('') : `<div class="dash-card">${empty('No active jobs. Accept a request to get started.')}</div>`);
}

async function loadCompleted() {
  loading('completed-services');
  const list = await fetchJobs('COMPLETED');
  setBody('completed-services', `<div class="dash-card" style="margin-bottom:16px;"><strong>${list.length}</strong> jobs completed successfully.</div>`
    + table(['Completed', 'Customer & Address', 'Service', 'Invoice', 'Rating'], list.map((r) => `<tr>
        <td>${fmtDateTime(r.completedAt)}<br><small class="text-muted">#${esc(r.bookingNo)}</small></td>
        <td>${esc(r.customer.fullName)}<br><small class="text-muted">${esc(r.address)}</small></td>
        <td>${esc(r.title)}</td>
        <td>${r.invoiceId ? money(r.invoiceTotal) : `<button class="btn-secondary-custom btn-sm" onclick="createInvoice(${r.id})">Create</button>`}</td>
        <td>${r.rating ? `<span class="stars">${stars(r.rating)}</span>` : '—'}</td></tr>`), 'No completed jobs yet.'));
}

async function advanceJob(id, status) {
  let body = { status };
  if (status === 'EN_ROUTE') {
    const v = await modal({ title: 'Heading to the customer', fields: [{ name: 'etaMinutes', label: 'ETA (minutes)', type: 'number', min: 0, max: 600, value: 20 }], submitText: 'Start Travel',
      validate: (x) => FC.numberError(x.etaMinutes, { label: 'ETA', min: 0, max: 600, integer: true, optional: true }) });
    if (!v) return;
    body.etaMinutes = Number(v.etaMinutes || 0);
    shareLocation(true);
  }
  if (status === 'COMPLETED' && !(await FC.confirmDialog('Mark this job as completed?', 'The customer will be notified.', 'Mark Completed'))) return;
  try {
    const r = await api.patch(`/api/provider/jobs/${id}/status`, body);
    toast(`Job status updated: ${r.statusLabel}`, 'success');
    if (status === 'COMPLETED' && !r.invoiceId) {
      if (await FC.confirmDialog('Generate the invoice now?', '', 'Create Invoice')) return createInvoice(id);
    }
    refreshActive();
  } catch (e) { showError(e); }
}

async function updateEta(id) {
  const v = await modal({
    title: 'Update customer ETA',
    fields: [{ name: 'etaMinutes', label: 'ETA (minutes)', type: 'number', min: 0, max: 600, required: true, value: 10 },
      { name: 'note', label: 'Message to customer (optional)', placeholder: 'Stuck in traffic, +10 mins' }],
    submitText: 'Send Update',
    validate: (x) => FC.firstError(FC.numberError(x.etaMinutes, { label: 'ETA', min: 0, max: 600, integer: true }),
      FC.textError(x.note, { label: 'Message', max: 300, optional: true })),
  });
  if (!v) return;
  try {
    await api.patch(`/api/provider/jobs/${id}/eta`, { etaMinutes: Number(v.etaMinutes), note: v.note || null });
    toast('Customer ETA updated', 'success');
    refreshActive();
  } catch (e) { showError(e); }
}

// Kept for the old inline handler names
function updateJobStatus() { switchSection('ongoing-services'); }

// ---------------------------------------------------------------- 9. customers
async function loadCustomers() {
  loading('customers');
  const list = await api.get('/api/provider/customers');
  setBody('customers', table(['Customer Name', 'Location', 'Total Jobs', 'Last Service', 'Contact'], list.map((c) => `<tr>
    <td><strong>${esc(c.name)}</strong></td><td>${esc(c.location)}</td><td>${c.totalJobs} Service${c.totalJobs === 1 ? '' : 's'}</td>
    <td>${fmtDate(c.lastServiceDate)}</td>
    <td>${c.phone ? `<a href="tel:${esc(c.phone.replace(/\s+/g, ''))}">${esc(c.phone)}</a>` : '—'}</td></tr>`), 'No customers yet.'));
}

// ---------------------------------------------------------------- 10. ETA & status broadcast
async function loadEtaStatus() {
  loading('eta-status');
  const jobs = (await fetchJobs('ALL')).filter((j) => ACTIVE.includes(j.status));
  if (!jobs.length) return setBody('eta-status', `<div class="dash-card">${empty('No active jobs to update.')}</div>`);
  const sel = state.selectedJobId && jobs.find((j) => j.id === state.selectedJobId) ? state.selectedJobId : jobs[0].id;
  setBody('eta-status', `<div class="dash-card" style="max-width: 560px;">
      <div class="form-group"><label>Active job</label>
        <select id="etaJob" class="form-control-custom">${jobs.map((j) => `<option value="${j.id}" ${j.id === sel ? 'selected' : ''}>#${esc(j.bookingNo)} · ${esc(j.customer.fullName)} · ${esc(j.statusLabel)}</option>`).join('')}</select></div>
      <button class="btn-primary-custom" style="width: 100%; margin-bottom: 10px;" onclick="broadcast('EN_ROUTE')"><i class="fa-solid fa-van-shuttle"></i> Broadcast: On the Way</button>
      <button class="btn-primary-custom" style="width: 100%; margin-bottom: 10px;" onclick="broadcast('ARRIVED')"><i class="fa-solid fa-location-dot"></i> Broadcast: Arrived at Location</button>
      <button class="btn-secondary-custom" style="width: 100%; margin-bottom: 10px;" onclick="broadcast('IN_PROGRESS')"><i class="fa-solid fa-screwdriver-wrench"></i> Broadcast: Started Repair Work</button>
      <button class="btn-secondary-custom" style="width: 100%; margin-bottom: 10px;" onclick="updateEta(+document.getElementById('etaJob').value)"><i class="fa-solid fa-clock"></i> Update ETA</button>
      <button class="btn-secondary-custom" style="width: 100%;" onclick="shareLocation()"><i class="fa-solid fa-location-crosshairs"></i> Share my live location</button>
      <div id="etaTimeline" style="margin-top:16px;"></div>
    </div>`);
  const select = document.getElementById('etaJob');
  const showTimeline = async () => {
    const ev = await api.get(`/api/provider/jobs/${select.value}/timeline`);
    document.getElementById('etaTimeline').innerHTML = ev.map((e) => `<p class="fc-muted"><strong>${esc(e.label)}</strong> · ${esc(e.message || '')} · ${fmtDateTime(e.at)}</p>`).join('');
  };
  select.addEventListener('change', () => { state.selectedJobId = +select.value; showTimeline().catch(showError); });
  showTimeline().catch(showError);
}

async function broadcast(status) {
  const id = +document.getElementById('etaJob').value;
  const job = (await fetchJobs('ALL')).find((j) => j.id === id);
  if (job && job.status === status) return toast('Already broadcast: ' + job.statusLabel);
  if (status === 'EN_ROUTE') return advanceJob(id, status);
  try {
    const r = await api.patch(`/api/provider/jobs/${id}/status`, { status });
    toast('Broadcast sent: ' + r.statusLabel, 'success');
    loadEtaStatus();
    syncLiveShare();
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- live location (Swiggy-style)
/* While any of my jobs is "On the way" (EN_ROUTE) the browser streams GPS to the server, which the
   customer's Live Tracking map polls. It stops automatically when I arrive / complete / cancel. */
const LiveShare = { watchId: null, heartbeat: null, lastSent: 0, lastPos: null, pending: null };
const LIVE_MIN_GAP_MS = 4000;     // never send more often than this
const LIVE_HEARTBEAT_MS = 15000;  // re-send even when standing still, so "updated N sec ago" stays fresh

async function syncLiveShare() {
  try {
    const jobs = await fetchJobs('ONGOING');
    if (jobs.some((j) => j.status === 'EN_ROUTE')) startLiveShare(); else stopLiveShare();
  } catch (e) { /* keep current state */ }
}

function startLiveShare() {
  if (LiveShare.watchId !== null || !navigator.geolocation) return;
  LiveShare.watchId = navigator.geolocation.watchPosition(
    (pos) => sendLivePosition([pos.coords.latitude, pos.coords.longitude]),
    (err) => { if (err.code === 1) { toast('Allow location access so the customer can track you live', 'error'); stopLiveShare(); } },
    { enableHighAccuracy: true, maximumAge: 3000, timeout: 20000 });
  LiveShare.heartbeat = setInterval(() => { if (LiveShare.lastPos) sendLivePosition(LiveShare.lastPos, true); }, LIVE_HEARTBEAT_MS);
  liveSharePill(true);
}

function stopLiveShare() {
  if (LiveShare.watchId !== null && navigator.geolocation) navigator.geolocation.clearWatch(LiveShare.watchId);
  clearInterval(LiveShare.heartbeat);
  clearTimeout(LiveShare.pending);
  Object.assign(LiveShare, { watchId: null, heartbeat: null, pending: null });
  liveSharePill(false);
}

function sendLivePosition(p, force) {
  LiveShare.lastPos = p;
  const wait = LIVE_MIN_GAP_MS - (Date.now() - LiveShare.lastSent);
  if (wait > 0 && !force) {           // too soon: send the newest position a little later
    clearTimeout(LiveShare.pending);
    LiveShare.pending = setTimeout(() => sendLivePosition(LiveShare.lastPos, true), wait);
    return;
  }
  LiveShare.lastSent = Date.now();
  api.put('/api/provider/location', { latitude: p[0], longitude: p[1] }).catch(() => {});
}

function liveSharePill(on) {
  let el = document.getElementById('liveSharePill');
  if (!on) { if (el) el.remove(); return; }
  if (el) return;
  el = document.createElement('div');
  el.id = 'liveSharePill';
  el.innerHTML = '<span></span> Sharing live location with your customer';
  el.style.cssText = 'position:fixed;right:18px;bottom:18px;z-index:9999;display:flex;align-items:center;gap:8px;'
    + 'background:#061a36;color:#fff;padding:10px 16px;border-radius:999px;font-size:.85rem;font-weight:700;'
    + 'box-shadow:0 10px 30px rgba(8,31,65,.3);';
  el.querySelector('span').style.cssText = 'width:9px;height:9px;border-radius:50%;background:#22c55e;box-shadow:0 0 0 4px rgba(34,197,94,.25);';
  document.body.appendChild(el);
}

function shareLocation(silent) {
  if (!navigator.geolocation) return silent || toast('Location is not available in this browser', 'error');
  navigator.geolocation.getCurrentPosition(async (pos) => {
    try {
      await api.put('/api/provider/location', { latitude: pos.coords.latitude, longitude: pos.coords.longitude });
      if (!silent) toast('Live location shared with your customers', 'success');
    } catch (e) { if (!silent) showError(e); }
  }, (err) => { if (!silent) toast('Could not read your location: ' + err.message, 'error'); }, { enableHighAccuracy: true, timeout: 10000 });
}

// ---------------------------------------------------------------- 11. photos
function openPhotos(jobId) {
  state.selectedJobId = jobId;
  switchSection('provider-photos');
}

async function loadPhotos() {
  loading('provider-photos');
  const jobs = (await fetchJobs('ALL')).filter((j) => j.status !== 'CANCELLED');
  if (!jobs.length) return setBody('provider-photos', `<div class="dash-card">${empty('Accept a job to upload before & after photos.')}</div>`);
  const sel = state.selectedJobId && jobs.find((j) => j.id === state.selectedJobId) ? state.selectedJobId : jobs[0].id;
  setBody('provider-photos', `<div class="dash-card" style="max-width: 860px;">
      <div class="form-group"><label>Job</label>
        <select id="photoJob" class="form-control-custom">${jobs.map((j) => `<option value="${j.id}" ${j.id === sel ? 'selected' : ''}>#${esc(j.bookingNo)} · ${esc(j.customer.fullName)} · ${esc(j.title)}</option>`).join('')}</select></div>
      <div class="grid-2-col">
        <label class="upload-dropzone" style="cursor:pointer;"><i class="fa-solid fa-camera"></i><p>Upload BEFORE Photo</p>
          <input type="file" accept="image/*" style="display:none" onchange="uploadJobPhoto('BEFORE', this)"></label>
        <label class="upload-dropzone" style="cursor:pointer;"><i class="fa-solid fa-camera"></i><p>Upload AFTER Photo</p>
          <input type="file" accept="image/*" style="display:none" onchange="uploadJobPhoto('AFTER', this)"></label>
      </div>
      <div id="photoGallery" class="fc-photo-grid" style="margin-top:16px;"></div>
    </div>`);
  const select = document.getElementById('photoJob');
  select.addEventListener('change', () => { state.selectedJobId = +select.value; renderGallery(); });
  renderGallery();
}

async function renderGallery() {
  const id = document.getElementById('photoJob').value;
  const photos = await api.get(`/api/provider/jobs/${id}/photos`);
  document.getElementById('photoGallery').innerHTML = photos.length ? photos.map((p) => `
    <figure><a href="${FC.fileUrl(p.url)}" target="_blank" rel="noopener"><img src="${FC.fileUrl(p.url)}" alt="${p.type}"></a>
    <figcaption>${p.type} · ${fmtDate(p.uploadedAt)}</figcaption></figure>`).join('') : empty('No photos for this job yet.');
}

async function uploadJobPhoto(type, input) {
  const file = input.files[0];
  if (!file) return;
  const id = document.getElementById('photoJob').value;
  try {
    await api.upload(`/api/provider/jobs/${id}/photos?type=${type}`, file);
    toast(`${type} photo uploaded`, 'success');
    renderGallery();
  } catch (e) { showError(e); } finally { input.value = ''; }
}

// ---------------------------------------------------------------- 12. invoices
async function loadInvoices() {
  loading('provider-invoices');
  const [invoices, jobs] = await Promise.all([api.get('/api/provider/invoices'), fetchJobs('ALL')]);
  const billable = jobs.filter((j) => (j.status === 'IN_PROGRESS' || j.status === 'COMPLETED') && !j.invoiceId);
  setBody('provider-invoices', `
    <div class="dash-card" style="max-width: 760px; margin-bottom:20px;">
      <h3 style="margin-top:0;">Generate &amp; Send Invoice</h3>
      ${billable.length ? `<div class="fc-row">
        <select id="invoiceJob" class="form-control-custom" style="flex:1;">${billable.map((j) => `<option value="${j.id}">#${esc(j.bookingNo)} · ${esc(j.customer.fullName)} · ${esc(j.title)}</option>`).join('')}</select>
        <button class="btn-primary-custom" onclick="createInvoice(+document.getElementById('invoiceJob').value)">Create Invoice</button></div>`
        : empty('Jobs that are in progress or completed without an invoice will appear here.')}
    </div>` + table(['Invoice', 'Customer', 'Service', 'Date', 'Amount', 'Status', 'PDF'], invoices.map((i) => `<tr>
      <td><strong>#${esc(i.invoiceNo)}</strong></td><td>${esc(i.customerName)}</td><td>${esc(i.service)}</td><td>${fmtDate(i.issuedAt)}</td>
      <td><strong>${money(i.total)}</strong><br><small class="text-muted">${money(i.laborCost)} labour + ${money(i.partsCost)} parts</small></td>
      <td>${badge(i.status)}</td>
      <td><button class="btn-secondary-custom btn-sm" onclick="FC.download('/api/invoices/${i.id}/pdf', '${esc(i.invoiceNo)}.pdf').catch(showError)"><i class="fa-solid fa-download"></i> PDF</button></td></tr>`), 'No invoices yet.'));
}

async function createInvoice(jobId) {
  const v = await modal({
    title: 'Create invoice',
    subtitle: 'The customer is notified and can pay from their dashboard.',
    fields: [
      { name: 'laborCost', label: 'Labour (₹)', type: 'number', min: 0, required: true, value: 499 },
      { name: 'partsCost', label: 'Parts (₹)', type: 'number', min: 0, value: 0 },
      { name: 'partsDescription', label: 'Parts used', placeholder: 'e.g. Capacitor 45uF' },
      { name: 'discount', label: 'Discount (₹)', type: 'number', min: 0, value: 0 },
      { name: 'notes', label: 'Notes', type: 'textarea' },
    ],
    submitText: 'Generate & Send',
    validate: (x) => FC.firstError(
      FC.numberError(x.laborCost, { label: 'Labour', min: 0, max: 1000000 }),
      FC.numberError(x.partsCost, { label: 'Parts', min: 0, max: 1000000, optional: true }),
      FC.numberError(x.discount, { label: 'Discount', min: 0, optional: true }),
      Number(x.discount || 0) > Number(x.laborCost || 0) + Number(x.partsCost || 0) ? 'Discount cannot be more than labour + parts.' : null,
      Number(x.partsCost || 0) > 0 && !String(x.partsDescription || '').trim() ? 'Please list the parts used.' : null,
      FC.textError(x.partsDescription, { label: 'Parts used', max: 500, optional: true }),
      FC.textError(x.notes, { label: 'Notes', max: 500, optional: true })),
  });
  if (!v) return;
  try {
    const inv = await api.post(`/api/provider/jobs/${jobId}/invoice`, {
      laborCost: Number(v.laborCost), partsCost: Number(v.partsCost || 0), partsDescription: v.partsDescription || null,
      discount: Number(v.discount || 0), notes: v.notes || null,
    });
    toast(`Invoice #${inv.invoiceNo} sent: ${money(inv.total)}`, 'success');
    refreshActive();
  } catch (e) { showError(e); }
}

// ---------------------------------------------------------------- 13. warranties
async function loadWarranties() {
  loading('provider-warranty');
  const list = await api.get('/api/provider/warranties');
  const active = list.filter((w) => w.status === 'ACTIVE').length;
  setBody('provider-warranty', `<div class="dash-card" style="margin-bottom:16px;"><strong>${active}</strong> active warranty certificate(s) of ${list.length} issued.</div>`
    + table(['Warranty', 'Customer', 'Service', 'Valid until', 'Status'], list.map((w) => `<tr>
      <td><strong>#${esc(w.warrantyNo)}</strong></td><td>${esc(w.customerName)}</td><td>${esc(w.service)}</td>
      <td>${fmtDate(w.validUntil)}${w.status === 'ACTIVE' ? `<br><small class="text-muted">${w.daysLeft} days left</small>` : ''}</td>
      <td>${badge(w.status)}${w.claimNote ? `<br><small class="text-muted">${esc(w.claimNote)}</small>` : ''}</td></tr>`), 'No warranties issued yet.'));
}

// ---------------------------------------------------------------- 14. ratings
async function loadRatings() {
  loading('ratings');
  const r = await api.get('/api/provider/reviews');
  const max = Math.max(1, ...Object.values(r.distribution));
  setBody('ratings', `
    <div class="grid-2-col" style="margin-bottom:20px;">
      <div class="dash-card"><h3 style="margin-top:0;">Overall Rating: ${r.average.toFixed(1)} / 5.0</h3>
        <p class="fc-muted">${r.count} reviews · <span class="stars">${stars(r.average)}</span></p></div>
      <div class="dash-card">${[5, 4, 3, 2, 1].map((n) => `
        <div class="fc-row" style="margin-bottom:6px;"><span style="width:30px;">${n}★</span>
          <div class="fc-bar" style="flex:1;"><div style="width:${(100 * (r.distribution[n] || 0)) / max}%;"></div></div>
          <span style="width:30px;text-align:right;">${r.distribution[n] || 0}</span></div>`).join('')}</div>
    </div>
    <div class="dash-card">${r.reviews.length ? r.reviews.map((x) => `
      <div class="fc-list-item"><div class="fc-row"><strong>${esc(x.customerName)} · ${esc(x.serviceTitle)}</strong><span class="stars">${stars(x.rating)}</span></div>
        <p class="fc-muted">"${esc(x.comment || '')}"</p><small style="color: var(--slate-400);">${fmtDate(x.createdAt)}</small></div>`).join('')
      : empty('No reviews in the system yet.')}</div>`);
}

// ---------------------------------------------------------------- 15. history
async function loadHistory() {
  loading('provider-history');
  const list = await fetchJobs('ALL');
  setBody('provider-history', table(['Date', 'Customer', 'Service', 'Status', 'Amount'], list.map((r) => `<tr>
    <td>${fmtDate(r.completedAt || r.preferredDate)}<br><small class="text-muted">#${esc(r.bookingNo || r.ticketNo)}</small></td>
    <td>${esc(r.customer.fullName)}</td><td>${esc(r.title)}</td><td>${badge(r.status)}</td><td>${money(r.invoiceTotal)}</td></tr>`), 'No jobs yet.'));
}

// ---------------------------------------------------------------- 16. performance
async function loadPerformance() {
  loading('performance');
  const p = await api.get('/api/provider/performance?months=6');
  const maxE = Math.max(1, ...p.monthly.map((m) => Number(m.earnings)));
  const stat = (label, value, sub, color) => `<div class="stat-card-modern"><div class="stat-icon-wrapper ${color}"><i class="fa-solid fa-chart-line"></i></div>
    <div class="stat-content"><label>${label}</label><strong>${value}</strong><span class="stat-subtext">${sub}</span></div></div>`;
  setBody('performance', `
    <div class="stats-grid-4" style="margin-bottom:20px;">
      ${stat('Total Earnings', money(p.totalEarnings), `${money(p.pendingPayments)} pending`, 'green')}
      ${stat('Completed Jobs', p.totalCompleted, `${p.totalCancelled} cancelled`, 'blue')}
      ${stat('Acceptance Rate', p.acceptanceRate + '%', `Completion ${p.completionRate}%`, 'amber')}
      ${stat('Rating', p.averageRating.toFixed(1), `${p.reviewCount} reviews`, 'purple')}
    </div>
    <div class="dash-card"><h3 style="margin-top:0;">Monthly Earnings &amp; Activity</h3>
      ${p.monthly.map((m) => `<div class="perf-row">
        <span class="perf-month">${esc(m.month)}</span>
        <div class="fc-bar perf-bar"><div style="width:${(100 * Number(m.earnings)) / maxE}%;"></div></div>
        <span class="perf-meta fc-muted">${m.jobs} jobs · ${money(m.earnings)}</span></div>`).join('')}
    </div>`);
}

// ---------------------------------------------------------------- 17. notifications
async function loadNotifications() {
  loading('provider-notifications');
  const r = await api.get('/api/notifications');
  setBadge('provider-notifications', r.unreadCount);
  setBody('provider-notifications', `<div class="dash-card">
    <div class="fc-row" style="margin-bottom:12px;"><span class="fc-muted">${r.unreadCount} unread</span>
      ${r.unreadCount ? '<button class="btn-secondary-custom btn-sm" onclick="markAllRead()">Mark all read</button>' : ''}</div>
    ${r.notifications.length ? r.notifications.map((n) => `
      <div style="padding: 10px 0; border-bottom: 1px solid var(--fcd-bd-ecf1f8, var(--slate-200)); ${n.read ? 'opacity:.7;' : ''}" class="fc-row">
        <div><strong style="color: var(--fcd-tx-061a36, var(--navy-900));">${n.read ? '' : '● '}${esc(n.title)}</strong>
          <p style="margin: 2px 0 0; color: var(--fcd-tx-5f6f87, var(--slate-500)); font-size: 0.85rem;">${esc(n.message)}</p>
          <small style="color: var(--slate-400);">${fmtDateTime(n.createdAt)}</small></div>
        <div style="display:flex;gap:6px;">
          ${n.type === 'REQUEST' || n.type === 'SOS' ? `<button class="btn-primary-custom btn-sm" onclick="switchSection('requests')">View</button>` : ''}
          ${n.read ? '' : `<button class="btn-secondary-custom btn-sm" onclick="markRead(${n.id})">Mark read</button>`}</div>
      </div>`).join('') : empty('You are all caught up!')}</div>`);
}
async function markRead(id) { try { await api.patch(`/api/notifications/${id}/read`); loadNotifications(); } catch (e) { showError(e); } }
async function markAllRead() { try { await api.patch('/api/notifications/read-all'); loadNotifications(); } catch (e) { showError(e); } }

// ---------------------------------------------------------------- 18. profile
async function loadProfile() {
  loading('provider-profile');
  const [p, cats, ver] = await Promise.all([api.get('/api/provider/profile'), FC.categories(), api.get('/api/provider/verification')]);
  setBody('provider-profile', `<div class="dash-card" style="max-width: 760px;">
    <form id="techProfileForm">
      <div style="display: flex; gap: 20px; align-items: center; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--fcd-bd-ecf1f8, var(--slate-200));">
        <div id="dashPhotoBox"><img class="fc-my-avatar" id="dashPhoto" src="${FC.avatarSrc(p.fullName, (FC.getUser() || {}).photoUrl)}" alt="${esc(p.fullName)}" style="width: 80px; height: 80px; border-radius: 50%; object-fit: cover; border: 2px solid var(--fcd-bd-ecf1f8, var(--slate-200));" /></div>
        <div>${ver.proBadge ? '<span class="badge-pill-custom badge-verified"><i class="fa-solid fa-circle-check"></i> Verified FixConnect Pro</span>' : '<span class="badge-pill-custom badge-warning">Verification pending</span>'}
          <h3 style="margin: 4px 0 2px; color: var(--fcd-tx-061a36, var(--navy-900));">${esc(p.fullName)}</h3>
          <small style="color: var(--fcd-tx-5f6f87, var(--slate-500));">${esc(p.headline || '')} · ${p.experienceYears}+ Yrs Exp</small></div>
      </div>
      <div class="grid-2-col">
        <div class="form-group"><label>Full Name</label><input name="fullName" class="form-control-custom" value="${esc(p.fullName)}" required /></div>
        <div class="form-group"><label>Phone</label><input name="phone" type="tel" class="form-control-custom" value="${esc(p.phone || '')}" required /></div>
        <div class="form-group"><label>Email</label><input name="email" type="email" class="form-control-custom" value="${esc(p.email)}" required /></div>
        <div class="form-group"><label>Specialization Category</label><select name="categoryCode" class="form-control-custom">${cats.map((c) => `<option value="${c.code}" ${p.category && p.category.code === c.code ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}</select></div>
        <div class="form-group"><label>Headline</label><input name="headline" class="form-control-custom" value="${esc(p.headline || '')}" /></div>
        <div class="form-group"><label>Years of Experience</label><input name="experienceYears" type="number" min="0" class="form-control-custom" value="${p.experienceYears}" required /></div>
      </div>
      <div class="form-group"><label>Skills &amp; Expertise</label><input name="skills" class="form-control-custom" value="${esc(p.skills || '')}" required /></div>
      <div class="form-group"><label>Service Area</label><input name="serviceArea" class="form-control-custom" value="${esc(p.serviceArea || '')}" required /></div>
      <div style="display: flex; gap: 12px; margin-top: 16px;">
        <button type="submit" class="btn-primary-custom">Save Profile</button>
        <a href="provider-profile.html" class="btn-secondary-custom">Full profile page</a>
      </div>
    </form></div>`);
  FC.mountPhotoControls(document.getElementById('dashPhoto'), document.getElementById('dashPhotoBox'));
  document.getElementById('techProfileForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    Object.keys(f).forEach((k) => { if (typeof f[k] === 'string') f[k] = f[k].trim(); });
    const err = FC.firstError(
      FC.nameError(f.fullName, 'Full name'),
      FC.phoneError(f.phone),
      FC.emailError(f.email),
      FC.textError(f.headline, { label: 'Headline', max: 120, optional: true }),
      FC.numberError(f.experienceYears, { label: 'Experience (years)', min: 0, max: 60, integer: true }),
      FC.textError(f.skills, { label: 'Skills', min: 3, max: 500 }),
      FC.areaError(f.serviceArea));
    if (err) return toast(err, 'error');
    f.experienceYears = Number(f.experienceYears);
    try {
      await api.put('/api/provider/profile', f);
      toast('Technician profile saved!', 'success');
      loadDashboard().catch(() => {});
    } catch (err) { showError(err); }
  });
}

// ---------------------------------------------------------------- 19. settings
async function loadSettings() {
  loading('provider-settings');
  const s = await api.get('/api/provider/settings');
  setBody('provider-settings', `<div class="dash-card" style="max-width: 760px;">
    <form id="techSettingsForm">
      <div class="grid-2-col">
        <div class="form-group"><label>Service Radius (km)</label><input name="serviceRadiusKm" type="number" min="1" max="100" class="form-control-custom" value="${s.serviceRadiusKm}" /></div>
        <div class="form-group"><label>Max Daily Jobs</label><input name="maxDailyJobs" type="number" min="1" max="30" class="form-control-custom" value="${s.maxDailyJobs}" /></div>
      </div>
      <h3>Bank Payout Details</h3>
      <div class="grid-2-col">
        <div class="form-group"><label>Account Holder</label><input name="bankAccountHolder" class="form-control-custom" value="${esc(s.bankAccountHolder || '')}" /></div>
        <div class="form-group"><label>Account Number</label><input name="bankAccountNumber" class="form-control-custom" placeholder="${esc(s.bankAccountNumber || 'Enter account number')}" /></div>
        <div class="form-group"><label>IFSC</label><input name="bankIfsc" class="form-control-custom" value="${esc(s.bankIfsc || '')}" style="text-transform:uppercase;" /></div>
        <div class="form-group"><label>UPI ID</label><input name="upiId" class="form-control-custom" value="${esc(s.upiId || '')}" /></div>
      </div>
      <h3>Notifications</h3>
      ${[['smsAlerts', 'SMS job alerts'], ['whatsappUpdates', 'WhatsApp updates'], ['emailNotifications', 'E-mail notifications']].map(([k, l]) => `
        <label style="display:flex;align-items:center;gap:8px;margin-bottom:8px;"><input type="checkbox" name="${k}" ${s[k] ? 'checked' : ''} /> ${l}</label>`).join('')}
      <button type="submit" class="btn-primary-custom" style="margin-top:10px;">Save Preferences</button>
    </form></div>`);
  document.getElementById('techSettingsForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const g = (n) => form.elements[n].value.trim();
    const body = {
      serviceRadiusKm: Number(g('serviceRadiusKm')), maxDailyJobs: Number(g('maxDailyJobs')),
      bankAccountHolder: g('bankAccountHolder') || null, bankIfsc: g('bankIfsc').toUpperCase() || null, upiId: g('upiId') || null,
      smsAlerts: form.elements.smsAlerts.checked, whatsappUpdates: form.elements.whatsappUpdates.checked,
      emailNotifications: form.elements.emailNotifications.checked,
    };
    if (g('bankAccountNumber')) body.bankAccountNumber = g('bankAccountNumber');
    const err = FC.firstError(
      FC.numberError(g('serviceRadiusKm'), { label: 'Service radius', min: 1, max: 100, integer: true }),
      FC.numberError(g('maxDailyJobs'), { label: 'Max daily jobs', min: 1, max: 30, integer: true }),
      g('bankAccountHolder') ? FC.nameError(g('bankAccountHolder'), 'Account holder name') : null,
      FC.accountNoError(g('bankAccountNumber')),
      FC.ifscError(g('bankIfsc')),
      FC.upiError(g('upiId')));
    if (err) return toast(err, 'error');
    try {
      await api.put('/api/provider/settings', body);
      toast('Settings saved!', 'success');
      loadSettings();
    } catch (err) { showError(err); }
  });
}

// ---------------------------------------------------------------- loaders
const LOADERS = {
  dashboard: loadDashboard,
  requests: loadRequests,
  'my-services': loadServices,
  'ai-complaint': loadAiGuide,
  schedule: loadSchedule,
  'upcoming-bookings': loadUpcomingBookings,
  'ongoing-services': loadOngoing,
  'completed-services': loadCompleted,
  customers: loadCustomers,
  'eta-status': loadEtaStatus,
  'provider-photos': loadPhotos,
  'provider-invoices': loadInvoices,
  ratings: loadRatings,
  'provider-notifications': loadNotifications,
  'provider-profile': loadProfile,
  'provider-settings': loadSettings,
};
