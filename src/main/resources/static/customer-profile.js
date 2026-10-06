/* ==========================================================================
   FIXCONNECT AI - CUSTOMER PROFILE PAGE (connected to the API)
   GET/PUT /api/customer/profile, /api/customer/addresses/**, PUT /api/users/me/password
   ========================================================================== */
const FC = window.FixConnect;
const { api, esc, toast, showError } = FC;
const ME = FC.requireAuth('CUSTOMER');

document.addEventListener('DOMContentLoaded', () => {
  if (!ME) return;
  const toggleBtn = document.getElementById('sidebarToggle');
  const sidebar = document.getElementById('sidebar');
  if (toggleBtn && sidebar) toggleBtn.addEventListener('click', () => sidebar.classList.toggle('mobile-open'));

  loadProfile().catch((e) => { showError(e); FC.stopSkeletons(document); });
  loadAddresses().catch((e) => { showError(e); FC.stopSkeletons(document); });

  document.getElementById('profileForm').addEventListener('submit', saveProfile);
  document.getElementById('passwordForm').addEventListener('submit', changePassword);
});

let profile = null;

async function loadProfile() {
  const p = await api.get('/api/customer/profile');
  profile = p;
  document.querySelectorAll('.sidebar-user-name').forEach((el) => { el.textContent = p.fullName; });

  const info = document.querySelector('.active-service-info');
  if (info) {
    info.querySelector('h3').textContent = p.fullName;
    info.querySelector('p').textContent = 'Primary Address: ' + (p.primaryAddress || 'not set');
    const meta = info.querySelectorAll('.active-service-meta > span');
    if (meta[0]) meta[0].innerHTML = `<i class="fa-solid fa-envelope"></i> ${esc(p.email)}`;
    if (meta[1]) meta[1].innerHTML = `<i class="fa-solid fa-phone"></i> ${esc(p.phone || '-')}`;
    if (meta[2]) meta[2].innerHTML = `<i class="fa-solid fa-calendar-alt"></i> Member since ${new Date(p.memberSince).toLocaleDateString('en-GB', { month: 'short', year: 'numeric' })}`;
  }
  const img = document.querySelector('.active-tech-img');
  if (img) FC.mountPhotoControls(img, document.querySelector('.active-service-info'), Object.assign({}, FC.getUser(), { fullName: p.fullName }));

  const cards = document.querySelectorAll('.stats-grid-4 .stat-card-modern');
  const data = [
    [p.servicesCompleted, 'All-time total'],
    [String(p.activeRequests).padStart(2, '0'), 'Open requests & bookings'],
    [String(p.savedAddresses).padStart(2, '0'), 'Saved service locations'],
    [p.loyaltyPoints, 'Tier: ' + p.tier],
  ];
  cards.forEach((c, i) => {
    if (!data[i]) return;
    c.querySelector('strong').textContent = data[i][0];
    c.querySelector('.stat-subtext').textContent = data[i][1];
  });

  const set = (id, v) => { const el = document.getElementById(id); if (el) el.value = v || ''; };
  set('fullName', p.fullName);
  set('phone', p.phone);
  set('email', p.email);
  set('altPhone', p.altPhone);
  set('address', p.primaryAddress);
  document.getElementById('profileForm').classList.remove('sk-form');
  document.querySelectorAll('.sk-bg').forEach((el) => el.classList.remove('sk-bg'));
}

async function saveProfile(e) {
  e.preventDefault();
  const btn = e.target.querySelector('button[type="submit"]');
  const v = (id) => document.getElementById(id).value.trim();
  const err = FC.firstError(
    FC.nameError(v('fullName'), 'Full name'),
    FC.phoneError(v('phone')),
    FC.emailError(v('email')),
    FC.phoneError(v('altPhone'), true),
    FC.addressError(v('address'), { label: 'Primary address' }));
  if (err) return toast(err, 'error');
  if (v('altPhone') && v('altPhone').replace(/\D/g, '').slice(-10) === v('phone').replace(/\D/g, '').slice(-10)) {
    return toast('Alternate number must be different from your primary number.', 'error');
  }
  await FC.busy(btn, async () => {
    try {
      await api.put('/api/customer/profile', {
        fullName: v('fullName'), phone: v('phone'), email: v('email'), altPhone: v('altPhone'), primaryAddress: v('address'),
      });
      // keep the cached user in sync for other pages
      const u = FC.getUser();
      FC.store('fc_user', Object.assign(u, { fullName: v('fullName'), email: v('email'), phone: v('phone') }));
      toast('Profile details saved successfully!', 'success');
      await loadProfile();
      await loadAddresses();
    } catch (err) { showError(err); }
  });
}

async function changePassword(e) {
  e.preventDefault();
  const currentPassword = document.getElementById('currPass').value;
  const newPassword = document.getElementById('newPass').value;
  const pwErr = FC.passwordError(newPassword);
  if (pwErr) return toast(pwErr, 'error');
  try {
    const r = await api.put('/api/users/me/password', { currentPassword, newPassword });
    toast(r.message, 'success');
    e.target.reset();
  } catch (err) { showError(err); }
}

// ---------------------------------------------------------------- addresses
function addressContainer() {
  const card = document.getElementById('addAddressBtn').closest('.dash-card');
  return card.querySelector('div[style*="flex-direction: column"]');
}

async function loadAddresses() {
  const list = await api.get('/api/customer/addresses');
  const box = addressContainer();
  box.innerHTML = list.length ? list.map((a) => `
    <div style="background: var(--fcd-bg-f8fafc, var(--slate-50)); border: 1px solid var(--fcd-bd-ecf1f8, var(--slate-200)); border-radius: var(--radius-md); padding: 14px; display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap;">
      <div>
        <span class="badge-pill-custom ${a.primary ? 'badge-info' : 'badge-scheduled'}">${esc(a.label || 'Address')}${a.primary ? ' (Primary)' : ''}</span>
        <h4 style="margin: 4px 0 2px; color: var(--fcd-tx-061a36, var(--navy-900));">${esc(a.line1)}</h4>
        <p style="margin: 0; font-size: 0.82rem; color: var(--fcd-tx-5f6f87, var(--slate-500));">${esc([a.city, a.state].filter(Boolean).join(', '))}${a.pincode ? ' - ' + esc(a.pincode) : ''}</p>
      </div>
      <div style="display:flex;gap:6px;">
        ${a.primary ? '<button class="btn-secondary-custom btn-sm" disabled>Primary</button>'
          : `<button class="btn-secondary-custom btn-sm" onclick="makePrimary(${a.id})">Make Primary</button>`}
        <button class="btn-secondary-custom btn-sm" onclick="editAddress(${a.id})"><i class="fa-solid fa-pen"></i></button>
        ${a.primary ? '' : `<button class="btn-secondary-custom btn-sm" onclick="deleteAddress(${a.id})"><i class="fa-solid fa-trash"></i></button>`}
      </div>
    </div>`).join('') : '<div class="fc-empty">No saved addresses yet.</div>';
  window.__addresses = list;
}

function addressError(v) {
  return FC.firstError(
    FC.labelError(v.label),
    FC.addressError(v.line1),
    FC.cityError(v.city, 'City'),
    FC.cityError(v.state, 'State'),
    FC.pincodeError(v.pincode));
}

function addressFields(a) {
  a = a || {};
  return [
    { name: 'label', label: 'Label', value: a.label || 'Home', placeholder: 'Home / Office' },
    { name: 'line1', label: 'Address', required: true, value: a.line1 },
    { name: 'city', label: 'City', value: a.city || 'Hyderabad' },
    { name: 'state', label: 'State', value: a.state || 'Telangana' },
    { name: 'pincode', label: 'Pincode', value: a.pincode, placeholder: '6 digits' },
    { name: 'primary', label: 'Set as primary?', type: 'select', value: String(!!a.primary), options: [{ value: 'false', label: 'No' }, { value: 'true', label: 'Yes' }] },
  ];
}

async function saveAddress(id, v) {
  const body = { label: v.label, line1: v.line1, city: v.city || null, state: v.state || null, pincode: v.pincode || '', primary: v.primary === 'true' };
  try {
    if (id) await api.put('/api/customer/addresses/' + id, body);
    else await api.post('/api/customer/addresses', body);
    toast('Address saved', 'success');
    await loadAddresses();
    await loadProfile();
  } catch (e) { showError(e); }
}

async function addAddress() {
  const v = await FC.modal({ title: 'Add service location', fields: addressFields(), submitText: 'Save Address', validate: addressError });
  if (v) saveAddress(null, v);
}

async function editAddress(id) {
  const a = (window.__addresses || []).find((x) => x.id === id);
  const v = await FC.modal({ title: 'Edit address', fields: addressFields(a), submitText: 'Save Address', validate: addressError });
  if (v) saveAddress(id, v);
}

async function makePrimary(id) {
  try {
    await api.patch(`/api/customer/addresses/${id}/primary`);
    toast('Address set as primary.', 'success');
    await loadAddresses();
    await loadProfile();
  } catch (e) { showError(e); }
}

async function deleteAddress(id) {
  if (!(await FC.confirmDialog('Delete this address?', '', 'Delete', true))) return;
  try {
    await api.del('/api/customer/addresses/' + id);
    await loadAddresses();
    await loadProfile();
  } catch (e) { showError(e); }
}
