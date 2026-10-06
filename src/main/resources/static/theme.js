/* ==========================================================================
   FIXCONNECT AI - light / dark theme
   Loaded in <head> (before the page is drawn) so there is no white flash.
   First visit follows the device setting; the toggle button remembers the choice.
   ========================================================================== */
(function () {
  'use strict';
  var KEY = 'fc_theme';
  var root = document.documentElement;
  var MOON = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>';
  var SUN = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><circle cx="12" cy="12" r="4.5" fill="currentColor"/><g stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8"/></g></svg>';

  function saved() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function device() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  function current() { var s = saved(); return s === 'dark' || s === 'light' ? s : device(); }

  function apply(theme) {
    root.setAttribute('data-theme', theme);
    var btn = document.querySelector('.fc-theme-toggle');
    if (btn) {
      var dark = theme === 'dark';
      btn.innerHTML = dark ? SUN : MOON;
      btn.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
      btn.title = dark ? 'Light theme' : 'Dark theme';
    }
  }

  function toggle() {
    var next = current() === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem(KEY, next); } catch (e) { /* private mode: still switch for this page */ }
    apply(next);
  }

  function mount() {
    if (document.querySelector('.fc-theme-toggle')) return;
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'fc-theme-toggle';
    btn.addEventListener('click', toggle);
    var wrap = document.querySelector('.topbar .nav-wrap');
    if (wrap) {
      wrap.appendChild(btn);
      wrap.classList.add('has-theme-toggle');
      fit(wrap);
      window.addEventListener('resize', function () { fit(wrap); });
      if (document.fonts && document.fonts.ready) document.fonts.ready.then(function () { fit(wrap); });
    } else { btn.classList.add('fc-theme-toggle-float'); document.body.appendChild(btn); }
    apply(current());
  }

  // Narrow phones: if the toggle would push the SOS button over the logo text,
  // show the toggle as a small floating button (bottom-left) instead.
  function fit(wrap) {
    var btn = document.querySelector('.fc-theme-toggle');
    if (!btn) return;
    // measure with the toggle in the header
    wrap.classList.remove('theme-toggle-floating');
    btn.classList.remove('fc-theme-toggle-float');
    if (btn.parentNode !== wrap) wrap.appendChild(btn);
    if (window.innerWidth > 760) return;
    var text = wrap.querySelector('.brand-text') || wrap.querySelector('.brand');
    var next = wrap.querySelector('.main-nav .nav-button-accent') || btn;
    if (!text) return;
    if (next.getBoundingClientRect().left < text.getBoundingClientRect().right + 8) {
      wrap.classList.add('theme-toggle-floating');      // SOS goes back to its usual place
      btn.classList.add('fc-theme-toggle-float');
      document.body.appendChild(btn);                   // outside the sticky header
    }
  }

  apply(current());
  if (window.matchMedia) {
    var mq = window.matchMedia('(prefers-color-scheme: dark)');
    var onChange = function () { if (!saved()) apply(device()); };
    if (mq.addEventListener) mq.addEventListener('change', onChange); else if (mq.addListener) mq.addListener(onChange);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount); else mount();
  window.FixConnectTheme = { toggle: toggle, apply: apply, current: current };
})();
