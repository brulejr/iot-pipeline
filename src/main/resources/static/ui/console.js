/* The operator console's behaviour: theme, local times, dialogs, notices, and the live
   monitor's controls. Everything else is htmx attributes in the pages. */
(function () {
  'use strict';

  // ---- theme ----

  document.addEventListener('click', function (event) {
    if (!event.target.closest('[data-theme-toggle]')) return;
    var next = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-bs-theme', next);
    try { localStorage.setItem('console-theme', next); } catch (e) { /* private window */ }
  });

  // ---- times, shown in the browser's own zone ----

  var relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

  function relative(date) {
    var seconds = (date.getTime() - Date.now()) / 1000;
    var abs = Math.abs(seconds);
    if (abs < 45) return relativeFormat.format(Math.round(seconds), 'second');
    if (abs < 2700) return relativeFormat.format(Math.round(seconds / 60), 'minute');
    if (abs < 64800) return relativeFormat.format(Math.round(seconds / 3600), 'hour');
    return relativeFormat.format(Math.round(seconds / 86400), 'day');
  }

  function formatTimes(root) {
    root.querySelectorAll('time[datetime]').forEach(function (el) {
      var date = new Date(el.getAttribute('datetime'));
      if (isNaN(date.getTime())) return;
      var format = el.dataset.format || 'datetime';
      el.title = date.toLocaleString();
      if (format === 'relative') {
        el.textContent = relative(date);
        el.classList.toggle('just-now', Date.now() - date.getTime() < 15000);
      } else if (format === 'time') {
        // 24-hour, so the milliseconds read on from the seconds.
        el.textContent = date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })
          + '.' + String(date.getMilliseconds()).padStart(3, '0');
      } else {
        el.textContent = date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
      }
    });
  }

  setInterval(function () {
    document.querySelectorAll('time[data-format="relative"]').forEach(function (el) {
      var date = new Date(el.getAttribute('datetime'));
      el.textContent = relative(date);
      el.classList.toggle('just-now', Date.now() - date.getTime() < 15000);
    });
  }, 5000);

  // ---- notices ----

  function showToast(el) {
    if (el.dataset.shown) return;
    el.dataset.shown = 'true';
    el.addEventListener('hidden.bs.toast', function () { el.remove(); });
    bootstrap.Toast.getOrCreateInstance(el, { delay: el.classList.contains('text-bg-danger') ? 8000 : 4000 }).show();
  }

  window.consoleToast = function (message, level) {
    var container = document.getElementById('toasts');
    if (!container) return;
    var toast = document.createElement('div');
    toast.className = 'toast align-items-center border-0 text-bg-' + (level || 'danger');
    toast.setAttribute('role', 'status');
    var row = document.createElement('div');
    row.className = 'd-flex';
    var body = document.createElement('div');
    body.className = 'toast-body';
    body.textContent = message;
    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn-close btn-close-white me-2 m-auto';
    close.setAttribute('data-bs-dismiss', 'toast');
    close.setAttribute('aria-label', 'Close');
    row.append(body, close);
    toast.append(row);
    container.append(toast);
    showToast(toast);
  };

  new MutationObserver(function (mutations) {
    mutations.forEach(function (mutation) {
      mutation.addedNodes.forEach(function (node) {
        if (node.nodeType !== 1) return;
        if (node.classList.contains('toast')) showToast(node);
        node.querySelectorAll && node.querySelectorAll('.toast').forEach(showToast);
      });
    });
  }).observe(document.documentElement, { childList: true, subtree: true });

  document.addEventListener('htmx:responseError', function (event) {
    var xhr = event.detail.xhr;
    window.consoleToast('The server could not do that (' + xhr.status + ' ' + (xhr.statusText || 'error') + ').');
  });
  document.addEventListener('htmx:sendError', function () {
    window.consoleToast('The application is not answering. Is it running?');
  });

  // ---- dialogs ----

  document.addEventListener('htmx:afterSwap', function (event) {
    var target = event.detail.target;
    if (target && target.id === 'modal-content' && target.children.length > 0) {
      bootstrap.Modal.getOrCreateInstance(document.getElementById('modal')).show();
    }
  });

  document.addEventListener('closeModal', function () {
    var modal = document.getElementById('modal');
    if (modal) bootstrap.Modal.getOrCreateInstance(modal).hide();
  });

  // A dialog left open would otherwise survive a boosted page change behind the new page.
  document.addEventListener('htmx:beforeSwap', function (event) {
    if (event.detail.boosted) {
      document.querySelectorAll('.modal.show').forEach(function (m) { bootstrap.Modal.getOrCreateInstance(m).hide(); });
      document.querySelectorAll('.modal-backdrop').forEach(function (b) { b.remove(); });
      document.body.classList.remove('modal-open');
      document.body.style.removeProperty('overflow');
      document.body.style.removeProperty('padding-right');
    }
  });

  // hx-confirm asks in a dialog rather than the browser's own prompt.
  document.addEventListener('htmx:confirm', function (event) {
    if (!event.detail.question) return;
    event.preventDefault();
    var dialog = document.getElementById('confirm');
    dialog.querySelector('.confirm-question').textContent = event.detail.question;
    var ok = dialog.querySelector('[data-confirm-ok]');
    ok.textContent = event.detail.elt.getAttribute('data-confirm-label') || 'Confirm';
    var modal = bootstrap.Modal.getOrCreateInstance(dialog);
    var confirmed = function () {
      modal.hide();
      event.detail.issueRequest(true);
    };
    ok.addEventListener('click', confirmed, { once: true });
    dialog.addEventListener('hidden.bs.modal', function () { ok.removeEventListener('click', confirmed); }, { once: true });
    modal.show();
  });

  // ---- curation form ----

  /** The next free row index for a form whose rows can be added and removed. */
  window.consoleNextIndex = function (id) {
    var el = document.getElementById(id);
    var next = parseInt(el.dataset.nextIndex || '0', 10);
    el.dataset.nextIndex = String(next + 1);
    return next;
  };

  /** A boolean field can only be read as a binary sensor, so choosing one says so. */
  window.consoleSuggestType = function (select) {
    var option = select.selectedOptions[0];
    var row = select.closest('tr');
    if (!option || !row) return;
    var type = row.querySelector('select[name$=".type"]');
    if (option.dataset.type === 'boolean' && type) type.value = 'BINARY';
  };

  // ---- live monitor ----

  var FEED_LIMIT = 300;
  var paused = false;
  var missed = 0;

  function feedEl() { return document.getElementById('feed-list'); }

  function setStatus(state, text) {
    var status = document.getElementById('live-status');
    if (!status) return;
    status.querySelector('.status-dot').className = 'status-dot ' + state;
    status.querySelector('.live-text').textContent = text;
  }

  function updateCounters() {
    var count = document.getElementById('feed-count');
    var list = feedEl();
    if (count && list) count.textContent = list.children.length;
    var missedEl = document.getElementById('feed-missed');
    if (missedEl) {
      missedEl.textContent = missed ? missed + ' held back while paused' : '';
    }
  }

  document.addEventListener('htmx:sseOpen', function () { setStatus(paused ? 'warn' : 'live', paused ? 'Paused' : 'Live'); });
  document.addEventListener('htmx:sseError', function () { setStatus('down', 'Reconnecting…'); });
  document.addEventListener('htmx:sseBeforeMessage', function (event) {
    if (paused && event.detail.type === 'reading') {
      event.preventDefault();
      missed++;
      updateCounters();
    }
  });
  document.addEventListener('htmx:sseMessage', function () {
    var list = feedEl();
    if (!list) return;
    while (list.children.length > FEED_LIMIT) list.lastElementChild.remove();
    updateCounters();
  });

  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-feed-action]');
    if (!button) return;
    var action = button.dataset.feedAction;
    if (action === 'pause') {
      paused = !paused;
      if (!paused) missed = 0;
      button.innerHTML = paused
        ? '<i class="bi bi-play-fill"></i> Resume'
        : '<i class="bi bi-pause-fill"></i> Pause';
      setStatus(paused ? 'warn' : 'live', paused ? 'Paused' : 'Live');
      updateCounters();
    } else if (action === 'clear') {
      var list = feedEl();
      if (list) list.replaceChildren();
      missed = 0;
      updateCounters();
    }
  });

  document.addEventListener('change', function (event) {
    if (event.target.id === 'show-radio') {
      document.querySelectorAll('.feed').forEach(function (feed) {
        feed.classList.toggle('hide-radio', !event.target.checked);
      });
      try { localStorage.setItem('console-show-radio', event.target.checked ? '1' : '0'); } catch (e) {}
    }
  });

  // ---- every piece of content htmx adds, the first page included ----

  htmx.onLoad(function (root) {
    formatTimes(root);
    root.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (el) {
      bootstrap.Tooltip.getOrCreateInstance(el);
    });
    var radio = root.querySelector && root.querySelector('#show-radio');
    if (radio) {
      var show = false;
      try { show = localStorage.getItem('console-show-radio') === '1'; } catch (e) {}
      radio.checked = show;
      document.querySelectorAll('.feed').forEach(function (feed) { feed.classList.toggle('hide-radio', !show); });
    }
    if (root.querySelector && root.querySelector('#feed-list')) {
      paused = false;
      missed = 0;
      setStatus('', 'Connecting…');
      updateCounters();
    }
  });
})();
