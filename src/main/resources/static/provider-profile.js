/* ==========================================================================
   FIXCONNECT AI - PROVIDER PROFILE PAGE (connected to the API)
   /api/provider/profile, /verification, /availability, /settings, /profile/id-proof
   ========================================================================== */
const FC = window.FixConnect;
const { api, esc, toast, showError } = FC;
const ME = FC.requireAuth('PROVIDER');

document.addEventListener('DOMContentLoaded', () => {
  if (!ME) return;
  const toggleBtn = document.getElementById('sidebarToggle');
  const sidebar = document.getElementById('sidebar');
  if (toggleBtn && sidebar) toggleBtn.addEventListener('click', () => sidebar.classList.toggle('mobile-open'));

  init().catch((e) => { showError(e); FC.stopSkeletons(document); });
  document.getElementById('providerProfileForm').addEventListener('submit', saveProfile);
  document.getElementById('preferencesForm').addEventListener('submit', savePreferences);
});

async function init() {
  // Turn the free-text category field into a dropdown of real categories
  const cats = await FC.categories();
  const catInput = document.getElementById('category');
  if (catInput && catInput.tagName === 'INPUT') {
    const sel = document.createElement('select');
    sel.id = 'category';
    sel.className = catInput.className;
    sel.required = true;
    sel.innerHTML = cats.map((c) => `<option value="${c.code}">${esc(c.name)}</option>`).join('');
    catInput.replaceWith(sel);
  }
  await Promise.all([loadProfile(), loadVerification(), loadSettings(), loadQuickCounts()]);
}

/** Real counts for the "Dashboard Options" buttons (nothing shown when there is nothing to show). */
async function loadQuickCounts() {
  try {
    const s = (await api.get('/api/provider/dashboard')).stats;
    const put = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };
    put('optRequests', s.newRequests ? `(${s.newRequests})` : '');
    put('optToday', s.todaysJobs ? `(${s.todaysJobs} job${s.todaysJobs === 1 ? '' : 's'})` : '');
    put('optRating', s.reviewCount ? `(${Number(s.rating).toFixed(1)}★)` : '');
  } catch (e) { /* leave the buttons without numbers */ }
}

async function loadProfile() {
  const p = await api.get('/api/provider/profile');
  document.querySelectorAll('.sidebar-user-name').forEach((el) => { el.textContent = p.fullName; });

  const info = document.querySelector('.active-service-info');
  if (info) {
    info.querySelector('h3').textContent = `${p.fullName} · ${p.headline || ''}`;
    info.querySelector('p').textContent = `Active Service Zone: ${p.serviceArea || '-'} (${p.serviceRadiusKm} km radius)`;
    const meta = info.querySelectorAll('.active-service-meta > span');
    const vals = [
      `<i class="fa-solid fa-briefcase"></i> ${p.experienceYears}+ Yrs Experience`,
      `<i class="fa-solid fa-star" style="color:var(--fcd-tx-f59e0b, #f59e0b);"></i> ${p.rating.toFixed(1)} (${p.reviewCount} reviews)`,
      `<i class="fa-solid fa-circle-check"></i> ${p.completionRate}% Job Completion`,
    ];
    meta.forEach((m, i) => { if (vals[i]) m.innerHTML = vals[i]; });
    const badgeEl = info.querySelector('.badge-pill-custom');
    if (badgeEl) {
      badgeEl.className = 'badge-pill-custom ' + (p.verified ? 'badge-verified' : 'badge-warning');
      badgeEl.innerHTML = p.verified ? '<i class="fa-solid fa-circle-check"></i> Verified FixConnect Pro' : 'Verification pending';
    }
  }

  const img = document.querySelector('.active-tech-img');
  if (img) FC.mountPhotoControls(img, document.querySelector('.active-service-info'), Object.assign({}, FC.getUser(), { fullName: p.fullName }));

  const cards = document.querySelectorAll('.stats-grid-4 .stat-card-modern');
  const data = [
    [p.rating.toFixed(1), `${p.reviewCount} Customer Reviews`],
    [p.completedJobs, `${p.completionRate}% Success Rate`],
    [`${p.serviceRadiusKm} km`, p.serviceArea || ''],
    [p.verified ? 'Pro Badge' : 'Pending', p.verified ? 'ID & Skills Verified' : 'Upload your ID proof'],
  ];
  cards.forEach((c, i) => {
    if (!data[i]) return;
    c.querySelector('strong').textContent = data[i][0];
    c.querySelector('.stat-subtext').textContent = data[i][1];
  });

  const set = (id, v) => { const el = document.getElementById(id); if (el) el.value = v ?? ''; };
  set('fullName', p.fullName);
  set('phone', p.phone);
  set('email', p.email);
  set('category', p.category ? p.category.code : '');
  set('skills', p.skills);
  set('experience', p.experienceYears);
  set('serviceArea', p.serviceArea);
  document.getElementById('providerProfileForm').classList.remove('sk-form');
  document.querySelectorAll('.sk-bg').forEach((el) => el.classList.remove('sk-bg'));

  const toggle = document.getElementById('availabilityToggle');
  if (toggle) { toggle.checked = p.available; paintAvailability(p.available); }
}

async function loadVerification() {
  const v = await api.get('/api/provider/verification');
  const card = [...document.querySelectorAll('.dash-card')].find((c) => c.textContent.includes('Verification & Credentials'));
  if (!card) return;
  const list = card.querySelector('div[style*="flex-direction: column"]');
  const item = (title, sub, ok, okLabel, extra) => `
    <div style="background: var(--fcd-bg-f8fafc, var(--slate-50)); border: 1px solid var(--fcd-bd-ecf1f8, var(--slate-200)); border-radius: var(--radius-md); padding: 14px; display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap;">
      <div><h4 style="margin: 0 0 2px; color: var(--fcd-tx-061a36, var(--navy-900));">${title}</h4><p style="margin: 0; font-size: 0.82rem; color: var(--fcd-tx-5f6f87, var(--slate-500));">${sub}</p></div>
      <div style="display:flex;gap:8px;align-items:center;">${extra || ''}
        ${ok ? `<span class="badge-pill-custom badge-verified"><i class="fa-solid fa-check"></i> ${okLabel}</span>` : '<span class="badge-pill-custom badge-warning">Pending</span>'}</div>
    </div>`;
  list.innerHTML =
    item('Government Photo ID (Aadhaar / PAN)',
      v.idVerified ? 'Verified on ' + FC.fmtDate(v.idVerifiedAt) : (v.idProofUploaded ? 'Uploaded - under review by FixConnect' : 'Not uploaded yet'),
      v.idVerified, 'Verified',
      `${v.idProofUrl ? `<a class="btn-secondary-custom btn-sm" target="_blank" rel="noopener" href="${FC.fileUrl(v.idProofUrl)}">View</a>` : ''}
       <label class="btn-secondary-custom btn-sm" style="cursor:pointer;"><i class="fa-solid fa-upload"></i> ${v.idProofUploaded ? 'Replace' : 'Upload'}
         <input type="file" accept="image/*,application/pdf" style="display:none" onchange="uploadIdProof(this)"></label>`)
    + item('Trade License / Skill Certification', v.licenseVerified ? 'Verified by FixConnect' : 'Pending review', v.licenseVerified, 'Verified')
    + item('Background Check', v.backgroundCheckCleared ? 'Police record check cleared' : 'Pending review', v.backgroundCheckCleared, 'Cleared');
}

async function uploadIdProof(input) {
  const file = input.files[0];
  if (!file) return;
  try {
    await api.upload('/api/provider/profile/id-proof', file);
    toast('ID proof uploaded - our team will verify it shortly.', 'success');
    loadVerification();
    loadProfile();
  } catch (e) { showError(e); } finally { input.value = ''; }
}

async function loadSettings() {
  const s = await api.get('/api/provider/settings');
  document.getElementById('radiusInput').value = s.serviceRadiusKm;
  document.getElementById('maxJobs').value = s.maxDailyJobs;
  document.getElementById('preferencesForm').classList.remove('sk-form');
}

async function saveProfile(e) {
  e.preventDefault();
  const btn = e.target.querySelector('button[type="submit"]');
  const v = (id) => document.getElementById(id).value.trim();
  const err = FC.firstError(
    FC.nameError(v('fullName'), 'Full name'),
    FC.phoneError(v('phone')),
    FC.emailError(v('email')),
    FC.textError(v('skills'), { label: 'Skills', min: 3, max: 500 }),
    FC.numberError(v('experience'), { label: 'Experience (years)', min: 0, max: 60, integer: true }),
    FC.areaError(v('serviceArea')));
  if (err) return toast(err, 'error');
  await FC.busy(btn, async () => {
    try {
      await api.put('/api/provider/profile', {
        fullName: v('fullName'), phone: v('phone'), email: v('email'), categoryCode: v('category'),
        skills: v('skills'), experienceYears: Number(v('experience')), serviceArea: v('serviceArea'),
      });
      const u = FC.getUser();
      FC.store('fc_user', Object.assign(u, { fullName: v('fullName'), email: v('email'), phone: v('phone') }));
      toast('Technician profile saved successfully!', 'success');
      loadProfile();
    } catch (err) { showError(err); }
  });
}

async function savePreferences(e) {
  e.preventDefault();
  const err = FC.firstError(
    FC.numberError(document.getElementById('radiusInput').value, { label: 'Service radius', min: 1, max: 100, integer: true }),
    FC.numberError(document.getElementById('maxJobs').value, { label: 'Max daily jobs', min: 1, max: 30, integer: true }));
  if (err) return toast(err, 'error');
  try {
    await api.put('/api/provider/settings', {
      serviceRadiusKm: Number(document.getElementById('radiusInput').value),
      maxDailyJobs: Number(document.getElementById('maxJobs').value),
    });
    toast('Preferences updated successfully!', 'success');
    loadProfile();
  } catch (err) { showError(err); }
}

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
  } catch (e) { input.checked = !input.checked; showError(e); }
}
