let state = null;
let activeView = 'home';
let toastTimer = null;

const viewTitles = {
  home: 'Home Dashboard',
  pair: 'Pair Device',
  devices: 'Paired Devices',
  history: 'Clipboard History',
  logs: 'Debug Logs'
};

const els = {
  viewTitle: document.getElementById('viewTitle'),
  syncToggle: document.getElementById('syncToggle'),
  statusText: document.getElementById('statusText'),
  ipAddress: document.getElementById('ipAddress'),
  pairingCode: document.getElementById('pairingCode'),
  deviceId: document.getElementById('deviceId'),
  deviceNameInput: document.getElementById('deviceNameInput'),
  identityForm: document.getElementById('identityForm'),
  regenCode: document.getElementById('regenCode'),
  sendClipboard: document.getElementById('sendClipboard'),
  manualPairForm: document.getElementById('manualPairForm'),
  pairIp: document.getElementById('pairIp'),
  pairPort: document.getElementById('pairPort'),
  pairCode: document.getElementById('pairCode'),
  qrImage: document.getElementById('qrImage'),
  qrPairingCode: document.getElementById('qrPairingCode'),
  latestHistory: document.getElementById('latestHistory'),
  nearbyList: document.getElementById('nearbyList'),
  devicesList: document.getElementById('devicesList'),
  historyList: document.getElementById('historyList'),
  logsList: document.getElementById('logsList'),
  toast: document.getElementById('toast')
};

function formatTime(timestamp) {
  if (!timestamp) {
    return 'Never';
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    month: 'short',
    day: 'numeric'
  }).format(new Date(timestamp));
}

function showToast(message) {
  clearTimeout(toastTimer);
  els.toast.textContent = message;
  els.toast.classList.add('visible');
  toastTimer = setTimeout(() => els.toast.classList.remove('visible'), 3200);
}

function setActiveView(view) {
  activeView = view;
  document.querySelectorAll('.view').forEach((node) => node.classList.toggle('active', node.id === view));
  document.querySelectorAll('.nav-item').forEach((node) => node.classList.toggle('active', node.dataset.view === view));
  els.viewTitle.textContent = viewTitles[view];
}

function render(nextState) {
  state = nextState;
  els.syncToggle.textContent = state.running ? 'Stop Sync' : 'Start Sync';
  els.statusText.textContent = state.running ? 'Running' : 'Stopped';
  els.ipAddress.textContent = state.ipAddress || '-';
  els.pairingCode.textContent = state.pairingCode || '-';
  els.deviceId.textContent = state.deviceId || '-';
  els.deviceNameInput.value = state.deviceName || '';
  els.qrPairingCode.textContent = state.pairingCode || '-';
  if (state.qrDataUrl) {
    els.qrImage.src = state.qrDataUrl;
  }
  renderLatestHistory(state.history || []);
  renderNearby(state.nearbyDevices || []);
  renderDevices(state.devices || []);
  renderHistory(state.history || []);
  renderLogs(state.logs || []);
}

function renderLatestHistory(history) {
  if (!history.length) {
    els.latestHistory.className = 'empty-state';
    els.latestHistory.textContent = 'No clipboard history yet.';
    return;
  }

  const item = history[0];
  els.latestHistory.className = 'history-item';
  els.latestHistory.innerHTML = `
    <span class="badge">${escapeHtml(item.direction)}</span>
    <div class="history-text">${escapeHtml(item.text)}</div>
    <div class="item-meta">${escapeHtml(item.sourceDeviceName || 'This desktop')} - ${formatTime(item.createdAt)}</div>
  `;
}

function renderNearby(devices) {
  if (!devices.length) {
    els.nearbyList.innerHTML = '<div class="empty-state">No nearby devices discovered yet.</div>';
    return;
  }

  els.nearbyList.innerHTML = devices.map((device) => `
    <div class="list-item">
      <div>
        <div class="item-title">${escapeHtml(device.deviceName)}</div>
        <div class="item-meta">${escapeHtml(device.platform)} - ${escapeHtml(device.ipAddress)}:${device.port} - ${formatTime(device.lastSeen)}</div>
      </div>
      <button class="secondary-button nearby-pair" data-ip="${escapeAttr(device.ipAddress)}" data-port="${device.port}">Use IP</button>
    </div>
  `).join('');
}

function renderDevices(devices) {
  if (!devices.length) {
    els.devicesList.innerHTML = '<div class="empty-state">No paired devices yet.</div>';
    return;
  }

  els.devicesList.innerHTML = devices.map((device) => `
    <div class="list-item">
      <div>
        <div class="item-title">${escapeHtml(device.deviceName)}</div>
        <div class="item-meta">${escapeHtml(device.deviceId)}</div>
        <div class="item-meta">${escapeHtml(device.platform)} - ${escapeHtml(device.ipAddress)}:${device.port} - last seen ${formatTime(device.lastSeen)}</div>
        ${device.lastError ? `<div class="item-meta">Last error: ${escapeHtml(device.lastError)}</div>` : ''}
      </div>
      <button class="danger-button remove-device" data-device-id="${escapeAttr(device.deviceId)}">Remove</button>
    </div>
  `).join('');
}

function renderHistory(history) {
  if (!history.length) {
    els.historyList.innerHTML = '<div class="empty-state">No clipboard history yet.</div>';
    return;
  }

  els.historyList.innerHTML = history.map((item) => `
    <div class="history-item">
      <span class="badge">${escapeHtml(item.direction)}</span>
      <div class="history-text">${escapeHtml(item.text)}</div>
      <div class="item-meta">${escapeHtml(item.sourceDeviceName || 'This desktop')} - ${formatTime(item.createdAt)} - ${escapeHtml(item.eventId)}</div>
    </div>
  `).join('');
}

function renderLogs(logs) {
  if (!logs.length) {
    els.logsList.innerHTML = '<div class="empty-state">No logs yet.</div>';
    return;
  }

  els.logsList.innerHTML = logs.map((item) => `
    <div class="log-item">
      <span class="badge">${escapeHtml(item.level)}</span>
      <div>${escapeHtml(item.message)}</div>
      <div class="item-meta">${formatTime(item.createdAt)}</div>
    </div>
  `).join('');
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function escapeAttr(value) {
  return escapeHtml(value);
}

async function refreshState() {
  render(await window.syncMesh.getState());
}

document.querySelectorAll('.nav-item').forEach((button) => {
  button.addEventListener('click', () => setActiveView(button.dataset.view));
});

els.syncToggle.addEventListener('click', async () => {
  try {
    render(state.running ? await window.syncMesh.stopSync() : await window.syncMesh.startSync());
  } catch (error) {
    showToast(error.message);
  }
});

els.identityForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    render(await window.syncMesh.updateSettings({ deviceName: els.deviceNameInput.value.trim() }));
    showToast('Device name saved');
  } catch (error) {
    showToast(error.message);
  }
});

els.regenCode.addEventListener('click', async () => {
  try {
    render(await window.syncMesh.updateSettings({ regeneratePairingCode: true }));
    showToast('Pairing code regenerated');
  } catch (error) {
    showToast(error.message);
  }
});

els.sendClipboard.addEventListener('click', async () => {
  try {
    render(await window.syncMesh.sendCurrentClipboard());
    showToast('Current clipboard sent');
  } catch (error) {
    showToast(error.message);
  }
});

els.manualPairForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const ipAddress = els.pairIp.value.trim();
    const port = Number(els.pairPort.value || 8989);
    const pairingCode = els.pairCode.value.trim();
    if (!ipAddress) {
      throw new Error('Android IP address is required');
    }
    if (!Number.isFinite(port) || port < 1 || port > 65535) {
      throw new Error('Port must be between 1 and 65535');
    }
    if (!pairingCode) {
      throw new Error('Pairing code is required');
    }

    render(await window.syncMesh.pairManual({
      ipAddress,
      port,
      pairingCode
    }));
    els.pairCode.value = '';
    showToast('Device paired');
  } catch (error) {
    showToast(error.message);
  }
});

document.body.addEventListener('click', async (event) => {
  const removeButton = event.target.closest('.remove-device');
  if (removeButton) {
    await window.syncMesh.removeDevice(removeButton.dataset.deviceId);
    await refreshState();
    showToast('Device removed');
    return;
  }

  const nearbyButton = event.target.closest('.nearby-pair');
  if (nearbyButton) {
    els.pairIp.value = nearbyButton.dataset.ip;
    els.pairPort.value = nearbyButton.dataset.port || 8989;
    showToast('Nearby device IP copied to pairing form');
  }
});

window.syncMesh.onState((payload) => render(payload));
window.syncMesh.onLog((log) => {
  if (!state) {
    return;
  }
  render({ ...state, logs: [log, ...(state.logs || [])].slice(0, 300) });
});
window.syncMesh.onPairRequest((request) => {
  showToast(`Pair request accepted from ${request.deviceName}`);
  refreshState();
});
window.syncMesh.onHistory(() => refreshState());
window.syncMesh.onNearby((nearbyDevices) => {
  if (!state) {
    return;
  }
  render({ ...state, nearbyDevices });
});

setActiveView(activeView);
refreshState().catch((error) => showToast(error.message));
