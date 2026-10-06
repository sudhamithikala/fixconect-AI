/* ==========================================================================
   FIXCONNECT AI - ADMIN CONSOLE
   /api/admin/** : overview, provider verification, customers, deactivate / reactivate.
   Requires api.js (window.FixConnect).
   ========================================================================== */

const FC = window.FixConnect;
const { api, esc, fmtDate, fmtDateTime, timeAgo, stars, toast, showError, modal } = FC;

const ME = FC.requireAuth('ADMIN');

const state = {
  providers: {},          // id -> provider view (for inline buttons)
  customers: {},          // id -> customer view
  providerFilter: { status: 'ALL', verification: 'ALL', q: '' },
  customerFilter: { status: 'ALL', q: '' },
};

// ---------------------------------------------------------------- bootstrap & navigation
document.addEventListener('DOMContentLoaded', () => {
  if (!ME) return;
  document.querySelectorAll('.sidebar-link[data-section]').forEach((link) => {
    link.addEventListener('click', (e) => { e.preventDefault(); switchSection(link.dataset.section); });
  });
  const toggle = document.getElementById('sidebarToggle');
  const sidebar = document.getElementById('sidebar');
  if (toggle && sidebar) toggle.addEventListener('click', () => sidebar.classList.toggle('mobile-open'));

  const hash = location.hash.replace('#section-', '');
  switchSection(hash && document.getElementById('section-' + hash) ? hash : 'overview');
});

function switchSection(id) {
  document.querySelectorAll('.sidebar-link[data-section]').forEach((l) => l.classList.toggle('active', l.dataset.section === id));
  document.querySelectorAll('.dashboard-section').forEach((s) => s.classList.toggle('active', s.id === 'section-' + id));
  const sidebar = document.getElementById('sidebar');
  if (sidebar) sidebar.classList.remove('mobile-open');
  if (history.replaceState) history.replaceState(null, '', '#section-' + id);
  window.scrollTo({ top: 0, behavior: 'smooth' });
  const loader = LOADERS[id];
  if (loader) loader().catch(showError);
}

function refreshActive() {
  const active = document.querySelector('.dashboard-section.active');
  const id = active ? active.id.replace('section-', '') : 'overview';
  LOADERS[id]().catch(showError);
  if (id !== 'overview') updateBadges().catch(() => {});
}

// ---------------------------------------------------------------- helpers
function setBody(sectionId, html) {
  const section = document.getElementById('section-' + sectionId);
  [...section.children].forEach((c) => { if (!c.classList.contains('section-top-header')) c.remove(); });
  section.insertAdjacentHTML('beforeend', html);
}
const loading = (id) => setBody(id, '<div class="dash-card"><div class="fc-loading"><i class="fa-solid fa-spinner fa-spin"></i> Loading...</div></div>');
const empty = (t) => `<div class="fc-empty">${esc(t)}</div>`;

function setBadge(section, count) {
  const link = document.querySelector(`.sidebar-link[data-section="${section}"]`);
  if (!link) return;
  let b = link.querySelector('.nav-badge');
  if (!count) { if (b) b.remove(); return; }
  if (!b) { b = document.createElement('span'); b.className = 'nav-badge'; link.appendChild(b); }
  b.textContent = count;
}

async function updateBadges() {
  const d = await api.get('/api/admin/dashboard');
  setBadge('verification', d.stats.pendingVerification);
  setBadge('deactivated', d.stats.deactivatedCustomers + d.stats.deactivatedProviders);
  return d;
}

const statusBadge = (active) => active
  ? '<span class="badge-pill-custom badge-completed">Active</span>'
  : '<span class="badge-pill-custom badge-cancelled">Deactivated</span>';

function verificationBadge(p) {
  if (p.verified) return '<span class="badge-pill-custom badge-verified"><i class="fa-solid fa-circle-check"></i> Verified</span>';
  return '<span class="badge-pill-custom badge-warning">Pending verification</span>';
}

function checks(p) {
  const chip = (ok, label) => `<span class="check-chip ${ok ? 'ok' : ''}">${ok ? '✓' : '•'} ${label}</span>`;
  return `<div class="check-row">${chip(p.idVerified, 'ID')}${chip(p.licenseVerified, 'Licence')}${chip(p.backgroundCheckCleared, 'Background')}</div>`;
}

function stat(label, value, sub, color, icon, section) {
  return `<div class="stat-card-modern" ${section ? `style="cursor:pointer" onclick="switchSection('${section}')"` : ''}>
    <div class="stat-icon-wrapper ${color}"><i class="fa-solid ${icon}"></i></div>
    <div class="stat-content"><label>${label}</label><strong>${value}</strong><span class="stat-subtext">${sub}</span></div>
  </div>`;
}

function providerActions(p) {
  state.providers[p.id] = p;
  const a = [];
  if (p.active) {
    a.push(`<button class="btn-${p.verified ? 'secondary' : 'primary'}-custom btn-sm" onclick="openVerification(${p.id})">
      <i class="fa-solid fa-user-check"></i> ${p.verified ? 'Review' : 'Verify'}</button>`);
    a.push(`<button class="btn-secondary-custom btn-sm" style="color:var(--fcd-tx-b91c1c, #b91c1c);" onclick="changeStatus(${p.id}, false)"><i class="fa-solid fa-user-slash"></i> Deactivate</button>`);
  } else {
    a.push(`<button class="btn-primary-custom btn-sm" onclick="changeStatus(${p.id}, true)"><i class="fa-solid fa-rotate-left"></i> Reactivate</button>`);
  }
  if (p.idProofUrl) a.push(`<a class="btn-secondary-custom btn-sm" target="_blank" rel="noopener" href="${FC.fileUrl(p.idProofUrl)}"><i class="fa-solid fa-id-card"></i> ID proof</a>`);
  return `<div style="display:flex;gap:6px;flex-wrap:wrap;">${a.join('')}</div>`;
}

function customerActions(c) {
  state.customers[c.id] = c;
  return c.active
    ? `<button class="btn-secondary-custom btn-sm" style="color:var(--fcd-tx-b91c1c, #b91c1c);" onclick="changeStatus(${c.id}, false)"><i class="fa-solid fa-user-slash"></i> Deactivate</button>`
    : `<button class="btn-primary-custom btn-sm" onclick="changeStatus(${c.id}, true)"><i class="fa-solid fa-rotate-left"></i> Reactivate</button>`;
}

function providerRow(p) {
  return `<tr>
    <td><div class="admin-person"><strong>${esc(p.fullName)}</strong><small>${esc(p.email)}</small><small>${esc(p.phone || '')}</small></div></td>
    <td>${esc(p.category ? p.category.name : '-')}<br><small class="text-muted">${p.experienceYears}+ yrs · ${esc(p.serviceArea || '')}</small></td>
    <td>${verificationBadge(p)}<div style="margin-top:6px;">${checks(p)}</div>
      ${p.verificationNote ? `<small class="text-muted">${esc(p.verificationNote)}</small>` : ''}</td>
    <td>${statusBadge(p.active)}<br><small class="text-muted">${p.active ? (p.available ? 'Online' : 'Offline') : esc(p.deactivationReason || '')}</small></td>
    <td>${p.reviewCount ? `<span class="stars">${stars(p.rating)}</span> ${p.rating.toFixed(1)}<br><small class="text-muted">${p.completedJobs} jobs</small>` : '<small class="text-muted">New</small>'}</td>
    <td><small>${fmtDate(p.createdAt)}</small></td>
    <td>${providerActions(p)}</td>
  </tr>`;
}

function providerTable(list, emptyText) {
  return `<div class="dash-card"><div class="dash-table-container"><table class="dash-table">
    <thead><tr><th>Technician</th><th>Category</th><th>Verification</th><th>Account</th><th>Rating</th><th>Joined</th><th>Actions</th></tr></thead>
    <tbody>${list.length ? list.map(providerRow).join('') : `<tr><td colspan="7">${empty(emptyText)}</td></tr>`}</tbody>
  </table></div></div>`;
}

function customerRow(c) {
  return `<tr>
    <td><div class="admin-person"><strong>${esc(c.fullName)}</strong><small>${esc(c.email)}</small><small>${esc(c.phone || '')}</small></div></td>
    <td><small>${esc(c.primaryAddress || '-')}</small></td>
    <td>${c.totalRequests}<br><small class="text-muted">${c.completedRequests} completed</small></td>
    <td>${statusBadge(c.active)}${!c.active && c.deactivationReason ? `<br><small class="text-muted">${esc(c.deactivationReason)}</small>` : ''}</td>
    <td><small>${fmtDate(c.createdAt)}</small></td>
    <td>${customerActions(c)}</td>
  </tr>`;
}

function customerTable(list, emptyText) {
  return `<div class="dash-card"><div class="dash-table-container"><table class="dash-table">
    <thead><tr><th>Customer</th><th>Primary address</th><th>Requests</th><th>Account</th><th>Joined</th><th>Actions</th></tr></thead>
    <tbody>${list.length ? list.map(customerRow).join('') : `<tr><td colspan="6">${empty(emptyText)}</td></tr>`}</tbody>
  </table></div></div>`;
}

function tabs(name, options, current) {
  return `<div class="admin-tabs" data-tabs="${name}">${options.map(([v, l]) =>
    `<button class="admin-tab ${v === current ? 'active' : ''}" data-value="${v}">${l}</button>`).join('')}</div>`;
}

function wireTabs(sectionId, name, onChange) {
  document.querySelectorAll(`#section-${sectionId} [data-tabs="${name}"] .admin-tab`).forEach((b) => {
    b.addEventListener('click', () => onChange(b.dataset.value));
  });
}

function wireSearch(inputId, onChange) {
  const input = document.getElementById(inputId);
  let t;
  input.addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => onChange(input.value.trim()), 300); });
}

// ---------------------------------------------------------------- overview
async function loadOverview() {
  loading('overview');
  const d = await updateBadges();
  const s = d.stats;
  document.querySelectorAll('.sidebar-user-name').forEach((el) => { el.textContent = d.adminName; });
  setBody('overview', `
    <div class="stats-grid-4" style="margin-bottom:24px;">
      ${stat('Pending Verification', s.pendingVerification, 'Technicians awaiting approval', 'amber', 'fa-user-clock', 'verification')}
      ${stat('Service Providers', s.totalProviders, `${s.verifiedProviders} verified · ${s.onlineProviders} online`, 'blue', 'fa-user-gear', 'providers')}
      ${stat('Customers', s.totalCustomers, `${s.activeCustomers} active`, 'green', 'fa-users', 'customers')}
      ${stat('Deactivated', s.deactivatedCustomers + s.deactivatedProviders, `${s.deactivatedProviders} providers · ${s.deactivatedCustomers} customers`, 'purple', 'fa-user-slash', 'deactivated')}
    </div>
    <div class="grid-main-side">
      <div class="dash-card">
        <div class="dash-card-header">
          <h2 class="dash-card-title"><i class="fa-solid fa-user-check"></i> Awaiting Verification</h2>
          <a class="btn-secondary-custom btn-sm" onclick="switchSection('verification')">View all</a>
        </div>
        ${d.pendingProviders.length ? d.pendingProviders.map((p) => { state.providers[p.id] = p; return `
          <div class="fc-list-item fc-row">
            <div><strong>${esc(p.fullName)}</strong> · ${esc(p.category ? p.category.name : '')}
              <p class="fc-muted">${p.experienceYears}+ yrs · ${esc(p.serviceArea || '')} · joined ${timeAgo(p.createdAt)}</p>
              <div style="margin-top:6px;">${checks(p)}</div></div>
            <button class="btn-primary-custom btn-sm" onclick="openVerification(${p.id})">Verify</button>
          </div>`; }).join('') : empty('All service providers are verified. 🎉')}
      </div>
      <div>
        <div class="dash-card" style="margin-bottom:24px;">
          <h2 class="dash-card-title" style="margin-bottom:12px;"><i class="fa-solid fa-chart-simple"></i> Activity</h2>
          <div class="fc-row" style="margin-bottom:8px;"><span>Open requests</span><strong>${s.openRequests}</strong></div>
          <div class="fc-row"><span>Completed jobs</span><strong>${s.completedRequests}</strong></div>
        </div>
        <div class="dash-card">
          <div class="dash-card-header">
            <h2 class="dash-card-title"><i class="fa-solid fa-user-plus"></i> Newest Customers</h2>
            <a class="btn-secondary-custom btn-sm" onclick="switchSection('customers')">All</a>
          </div>
          ${d.recentCustomers.length ? d.recentCustomers.map((c) => `
            <div class="fc-row" style="padding:8px 0;border-bottom:1px solid var(--fcd-bd-ecf1f8, var(--slate-200));">
              <span><strong>${esc(c.fullName)}</strong><br><small class="text-muted">${esc(c.email)}</small></span>
              ${statusBadge(c.active)}
            </div>`).join('') : empty('No customers yet.')}
        </div>
      </div>
    </div>`);
}

// ---------------------------------------------------------------- pending verification
async function loadVerification() {
  loading('verification');
  const list = await api.get('/api/admin/providers?verification=PENDING&status=ACTIVE');
  setBadge('verification', list.length);
  setBody('verification', list.length ? list.map((p) => { state.providers[p.id] = p; return `
    <div class="dash-card" style="margin-bottom:16px;">
      <div style="display:flex;justify-content:space-between;gap:16px;flex-wrap:wrap;">
        <div style="flex:1;min-width:260px;">
          <span class="badge-pill-custom badge-warning">Pending verification</span>
          <h3 style="margin:6px 0 2px;color:var(--fcd-tx-061a36, var(--navy-900));">${esc(p.fullName)} · ${esc(p.category ? p.category.name : '')}</h3>
          <p class="fc-muted">${esc(p.email)} · ${esc(p.phone || '')}</p>
          <p class="fc-muted">${p.experienceYears}+ years · ${esc(p.serviceArea || '-')} · Skills: ${esc(p.skills || '-')}</p>
          <p class="fc-muted">Registered ${fmtDateTime(p.createdAt)}</p>
          <div style="margin-top:8px;">${checks(p)}</div>
          ${p.verificationNote ? `<p class="fc-muted">Note: ${esc(p.verificationNote)}</p>` : ''}
        </div>
        <div style="display:flex;flex-direction:column;gap:8px;align-items:flex-end;">
          ${p.idProofUrl ? `<a class="btn-secondary-custom btn-sm" target="_blank" rel="noopener" href="${FC.fileUrl(p.idProofUrl)}"><i class="fa-solid fa-id-card"></i> View ID proof</a>`
            : '<span class="badge-pill-custom badge-cancelled">No ID proof uploaded</span>'}
          <button class="btn-primary-custom btn-sm" onclick="approveAll(${p.id})"><i class="fa-solid fa-circle-check"></i> Approve</button>
          <button class="btn-secondary-custom btn-sm" onclick="openVerification(${p.id})">Review checks</button>
          <button class="btn-secondary-custom btn-sm" style="color:var(--fcd-tx-b91c1c, #b91c1c);" onclick="changeStatus(${p.id}, false)">Deactivate</button>
        </div>
      </div>
    </div>`; }).join('') : `<div class="dash-card">${empty('No technicians are waiting for verification.')}</div>`);
}

// ---------------------------------------------------------------- all providers
async function loadProviders() {
  const f = state.providerFilter;
  if (!document.getElementById('providerSearch')) {
    setBody('providers', `
      <div class="fc-filter-bar">
        <input id="providerSearch" type="search" placeholder="Search name, e-mail, phone, area, skill" style="flex:1;min-width:220px;" />
      </div>
      ${tabs('verification', [['ALL', 'All'], ['PENDING', 'Pending'], ['VERIFIED', 'Verified']], f.verification)}
      ${tabs('status', [['ALL', 'Any status'], ['ACTIVE', 'Active'], ['DEACTIVATED', 'Deactivated']], f.status)}
      <div id="providerTable"></div>`);
    wireTabs('providers', 'verification', (v) => { f.verification = v; loadProviders().catch(showError); });
    wireTabs('providers', 'status', (v) => { f.status = v; loadProviders().catch(showError); });
    wireSearch('providerSearch', (q) => { f.q = q; loadProviders().catch(showError); });
  }
  document.querySelectorAll('#section-providers [data-tabs="verification"] .admin-tab').forEach((b) => b.classList.toggle('active', b.dataset.value === f.verification));
  document.querySelectorAll('#section-providers [data-tabs="status"] .admin-tab').forEach((b) => b.classList.toggle('active', b.dataset.value === f.status));
  const q = new URLSearchParams({ status: f.status, verification: f.verification });
  if (f.q) q.set('q', f.q);
  const list = await api.get('/api/admin/providers?' + q);
  document.getElementById('providerTable').innerHTML = providerTable(list, 'No service providers match these filters.');
}

// ---------------------------------------------------------------- customers
async function loadCustomers() {
  const f = state.customerFilter;
  if (!document.getElementById('customerSearch')) {
    setBody('customers', `
      <div class="fc-filter-bar">
        <input id="customerSearch" type="search" placeholder="Search name, e-mail or phone" style="flex:1;min-width:220px;" />
      </div>
      ${tabs('status', [['ALL', 'All'], ['ACTIVE', 'Active'], ['DEACTIVATED', 'Deactivated']], f.status)}
      <div id="customerTable"></div>`);
    wireTabs('customers', 'status', (v) => { f.status = v; loadCustomers().catch(showError); });
    wireSearch('customerSearch', (q) => { f.q = q; loadCustomers().catch(showError); });
  }
  document.querySelectorAll('#section-customers [data-tabs="status"] .admin-tab').forEach((b) => b.classList.toggle('active', b.dataset.value === f.status));
  const q = new URLSearchParams({ status: f.status });
  if (f.q) q.set('q', f.q);
  const list = await api.get('/api/admin/customers?' + q);
  document.getElementById('customerTable').innerHTML = customerTable(list, 'No customers match these filters.');
}

// ---------------------------------------------------------------- deactivated
async function loadDeactivated() {
  loading('deactivated');
  const [providers, customers] = await Promise.all([
    api.get('/api/admin/providers?status=DEACTIVATED'),
    api.get('/api/admin/customers?status=DEACTIVATED'),
  ]);
  setBadge('deactivated', providers.length + customers.length);
  setBody('deactivated', `
    <h2 class="dash-card-title" style="margin:0 0 12px;"><i class="fa-solid fa-user-gear"></i> Service Providers (${providers.length})</h2>
    ${providerTable(providers, 'No deactivated service providers.')}
    <h2 class="dash-card-title" style="margin:24px 0 12px;"><i class="fa-solid fa-users"></i> Customers (${customers.length})</h2>
    ${customerTable(customers, 'No deactivated customers.')}`);
}

// ---------------------------------------------------------------- actions
async function approveAll(id) {
  const p = state.providers[id];
  const ok = await FC.confirmDialog(`Approve ${p.fullName}?`,
    'Marks ID, licence and background check as verified. The technician can then go online and receive jobs.', 'Approve');
  if (!ok) return;
  await saveVerification(id, { idVerified: true, licenseVerified: true, backgroundCheckCleared: true, note: null });
}

async function openVerification(id) {
  const p = state.providers[id];
  const yesNo = (v) => [{ value: 'true', label: 'Verified' }, { value: 'false', label: 'Not verified' }].map((o) => o);
  const v = await modal({
    title: `Verify ${p.fullName}`,
    subtitle: `${p.category ? p.category.name : ''} · ${p.experienceYears}+ yrs · ${p.serviceArea || ''}\n`
      + (p.idProofUploaded ? 'ID proof uploaded - open it from the list before approving.' : 'No ID proof uploaded yet.')
      + '\nAll three checks verified = approved.',
    fields: [
      { name: 'idVerified', label: 'Government Photo ID (Aadhaar / PAN)', type: 'select', value: String(p.idVerified), options: yesNo() },
      { name: 'licenseVerified', label: 'Trade licence / skill certification', type: 'select', value: String(p.licenseVerified), options: yesNo() },
      { name: 'backgroundCheckCleared', label: 'Background check', type: 'select', value: String(p.backgroundCheckCleared),
        options: [{ value: 'true', label: 'Cleared' }, { value: 'false', label: 'Not cleared' }] },
      { name: 'note', label: 'Note to the technician (optional)', type: 'textarea', value: p.verificationNote || '',
        placeholder: 'e.g. Please upload a clearer ID photo' },
    ],
    submitText: 'Save Verification',
  });
  if (!v) return;
  await saveVerification(id, {
    idVerified: v.idVerified === 'true', licenseVerified: v.licenseVerified === 'true',
    backgroundCheckCleared: v.backgroundCheckCleared === 'true', note: v.note || null,
  });
}

async function saveVerification(id, body) {
  try {
    const p = await api.patch(`/api/admin/providers/${id}/verification`, body);
    toast(p.verified ? `✅ ${p.fullName} is verified and can now receive jobs.` : `Verification saved for ${p.fullName}.`, 'success');
    refreshActive();
  } catch (e) { showError(e); }
}

async function changeStatus(id, activate) {
  const person = state.providers[id] || state.customers[id] || {};
  const isProvider = !!state.providers[id];
  let reason = null;
  if (activate) {
    const ok = await FC.confirmDialog(`Reactivate ${person.fullName}?`, 'They will be able to log in again.', 'Reactivate');
    if (!ok) return;
  } else {
    const v = await modal({
      title: `Deactivate ${person.fullName}?`,
      subtitle: (isProvider
        ? 'The technician is logged out immediately, goes offline, and bookings sent only to them are offered to other technicians.'
        : 'The customer is logged out immediately and their pending requests are cancelled.')
        + '\nYou can reactivate the account at any time.',
      fields: [{ name: 'reason', label: 'Reason (shared with the user by e-mail)', type: 'textarea', required: true,
        placeholder: 'e.g. Repeated no-shows reported by customers' }],
      submitText: 'Deactivate',
      danger: true,
    });
    if (!v) return;
    reason = v.reason;
  }
  try {
    const r = await api.patch(`/api/admin/users/${id}/status`, { active: activate, reason });
    toast(r.message, 'success');
    refreshActive();
  } catch (e) { showError(e); }
}

const LOADERS = {
  overview: loadOverview,
  verification: loadVerification,
  providers: loadProviders,
  customers: loadCustomers,
  deactivated: loadDeactivated,
};
