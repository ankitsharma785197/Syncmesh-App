let state = null;
let activeView = 'home';
let toastTimer = null;
let stagedFiles = []; // [{ path, name, size }]
let incomingOffer = null;
let dragDepth = 0;

const viewTitles = {
  home: 'Home',
  pair: 'Pair Device',
  devices: 'Paired Devices',
  transfer: 'File Transfer',
  history: 'Clipboard History',
  logs: 'Debug Logs'
};

const els = {
  versionLabel: document.getElementById('versionLabel'),
  debugNavItem: document.getElementById('debugNavItem'),
  themeToggle: document.getElementById('themeToggle'),
  themeToggleIcon: document.getElementById('themeToggleIcon'),
  themeToggleLabel: document.getElementById('themeToggleLabel'),
  clearHistory: document.getElementById('clearHistory'),
  clearTransferHistoryBtn: document.getElementById('clearTransferHistory'),
  copyLogs: document.getElementById('copyLogs'),
  clearLogs: document.getElementById('clearLogs'),
  debugOff: document.getElementById('debugOff'),
  onboarding: document.getElementById('onboarding'),
  onboardingIcon: document.getElementById('onboardingIcon'),
  onboardingTitle: document.getElementById('onboardingTitle'),
  onboardingText: document.getElementById('onboardingText'),
  onboardingDots: document.getElementById('onboardingDots'),
  onboardingSkip: document.getElementById('onboardingSkip'),
  onboardingBack: document.getElementById('onboardingBack'),
  onboardingNext: document.getElementById('onboardingNext'),
  tourOverlay: document.getElementById('tourOverlay'),
  tourHighlight: document.getElementById('tourHighlight'),
  tourCard: document.getElementById('tourCard'),
  tourTitle: document.getElementById('tourTitle'),
  tourText: document.getElementById('tourText'),
  tourStepCount: document.getElementById('tourStepCount'),
  tourSkip: document.getElementById('tourSkip'),
  tourNext: document.getElementById('tourNext'),
  viewTitle: document.getElementById('viewTitle'),
  syncToggle: document.getElementById('syncToggle'),
  sidebarStatus: document.getElementById('sidebarStatus'),
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
  toast: document.getElementById('toast'),
  // transfer
  dropZone: document.getElementById('dropZone'),
  chooseFiles: document.getElementById('chooseFiles'),
  stagedFilesEl: document.getElementById('stagedFiles'),
  transferDevice: document.getElementById('transferDevice'),
  sendFiles: document.getElementById('sendFiles'),
  clearFiles: document.getElementById('clearFiles'),
  transferNoDevices: document.getElementById('transferNoDevices'),
  goPair: document.getElementById('goPair'),
  openTransferFolder: document.getElementById('openTransferFolder'),
  chooseTransferFolder: document.getElementById('chooseTransferFolder'),
  resetTransferFolder: document.getElementById('resetTransferFolder'),
  saveDirName: document.getElementById('saveDirName'),
  saveDirPath: document.getElementById('saveDirPath'),
  incomingDest: document.getElementById('incomingDest'),
  launchAtLogin: document.getElementById('launchAtLogin'),
  startMinimized: document.getElementById('startMinimized'),
  notificationsEnabled: document.getElementById('notificationsEnabled'),
  transferProgressPanel: document.getElementById('transferProgressPanel'),
  transferProgressTitle: document.getElementById('transferProgressTitle'),
  transferProgressPeer: document.getElementById('transferProgressPeer'),
  transferPhaseBadge: document.getElementById('transferPhaseBadge'),
  transferOverallFill: document.getElementById('transferOverallFill'),
  transferOverallText: document.getElementById('transferOverallText'),
  transferSpeed: document.getElementById('transferSpeed'),
  transferEta: document.getElementById('transferEta'),
  transferFileName: document.getElementById('transferFileName'),
  transferFileFill: document.getElementById('transferFileFill'),
  transferFileText: document.getElementById('transferFileText'),
  transferCounter: document.getElementById('transferCounter'),
  transferMessage: document.getElementById('transferMessage'),
  transferPause: document.getElementById('transferPause'),
  transferResume: document.getElementById('transferResume'),
  transferCancel: document.getElementById('transferCancel'),
  transferRetry: document.getElementById('transferRetry'),
  transferDone: document.getElementById('transferDone'),
  transferHistoryList: document.getElementById('transferHistoryList'),
  // overlays
  dragOverlay: document.getElementById('dragOverlay'),
  incomingModal: document.getElementById('incomingModal'),
  incomingSummary: document.getElementById('incomingSummary'),
  incomingFiles: document.getElementById('incomingFiles'),
  incomingAccept: document.getElementById('incomingAccept'),
  incomingReject: document.getElementById('incomingReject')
};

// ── helpers ──────────────────────────────────────────────────────────────────

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

function formatBytes(bytes) {
  const value = Number(bytes) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatSpeed(bps) {
  return bps > 0 ? `${formatBytes(bps)}/s` : '';
}

function formatEta(seconds) {
  if (seconds == null || seconds < 0) return '';
  if (seconds < 60) return `${seconds}s left`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s left`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m left`;
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

// ── render ───────────────────────────────────────────────────────────────────

function render(nextState) {
  state = nextState;
  els.debugNavItem.classList.toggle('hidden', !state.debugUnlocked);
  if (!state.debugUnlocked && activeView === 'logs') {
    setActiveView('home');
  }
  els.syncToggle.textContent = state.running ? 'Stop Sync' : 'Start Sync';
  els.statusText.textContent = state.running ? 'Running' : 'Stopped';
  els.sidebarStatus.classList.toggle('running', Boolean(state.running));
  els.sidebarStatus.innerHTML = `<span class="dot"></span>${state.running ? 'Running' : 'Stopped'}`;
  els.ipAddress.textContent = state.ipAddress || '-';
  els.pairingCode.textContent = state.pairingCode || '-';
  els.deviceId.textContent = state.deviceId || '-';
  if (document.activeElement !== els.deviceNameInput) {
    els.deviceNameInput.value = state.deviceName || '';
  }
  els.qrPairingCode.textContent = state.pairingCode || '-';
  if (state.qrDataUrl) {
    els.qrImage.src = state.qrDataUrl;
  }
  renderLatestHistory(state.history || []);
  renderNearby(state.nearbyDevices || []);
  renderDevices(state.devices || []);
  renderHistory(state.history || []);
  renderLogs(state.logs || []);
  renderTransferDevices(state.devices || []);
  renderTransferHistory(state.transferHistory || []);
  renderTransfer(state.transfer);
  renderSaveLocation();
  els.launchAtLogin.checked = state.settings?.launchAtLogin === 'true';
  els.startMinimized.checked = state.settings?.startMinimized === 'true';
  els.notificationsEnabled.checked = state.settings?.notificationsEnabled !== 'false';
}

function prettyPath(fullPath) {
  if (!fullPath) {
    return '';
  }
  const home = state?.homeDir;
  return home && fullPath.startsWith(home) ? `~${fullPath.slice(home.length)}` : fullPath;
}

function renderSaveLocation() {
  const dir = state?.transferSaveDir || '';
  const name = dir.split(/[\\/]/).filter(Boolean).pop() || dir;
  els.saveDirName.textContent = name;
  els.saveDirPath.textContent = prettyPath(dir);
  els.saveDirPath.title = dir;
  els.resetTransferFolder.classList.toggle(
    'hidden',
    !state?.transferSaveDirDefault || dir === state.transferSaveDirDefault
  );
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

// ── transfer rendering ───────────────────────────────────────────────────────

function renderTransferDevices(devices) {
  const previous = els.transferDevice.value;
  els.transferDevice.innerHTML = devices.map((device) => `
    <option value="${escapeAttr(device.deviceId)}">${escapeHtml(device.deviceName)} (${escapeHtml(device.ipAddress)})</option>
  `).join('');
  if (previous && devices.some((device) => device.deviceId === previous)) {
    els.transferDevice.value = previous;
  }
  els.transferNoDevices.classList.toggle('hidden', devices.length > 0);
  updateSendButton();
}

function renderStagedFiles() {
  if (!stagedFiles.length) {
    els.stagedFilesEl.innerHTML = '';
  } else {
    els.stagedFilesEl.innerHTML = stagedFiles.map((file, index) => `
      <div class="staged-file">
        <span class="name">${escapeHtml(file.name)}</span>
        <span class="size">${formatBytes(file.size)}</span>
        <button class="remove" data-index="${index}" title="Remove">✕</button>
      </div>
    `).join('');
  }
  els.clearFiles.classList.toggle('hidden', !stagedFiles.length);
  updateSendButton();
}

function updateSendButton() {
  const transferActive = state?.transfer && !isTerminalPhase(state.transfer.phase);
  els.sendFiles.disabled = !stagedFiles.length || !els.transferDevice.value || Boolean(transferActive);
}

function isTerminalPhase(phase) {
  return phase === 'completed' || phase === 'failed' || phase === 'cancelled';
}

const phaseLabels = {
  connecting: 'Connecting',
  waiting_for_accept: 'Waiting for accept',
  incoming_pending: 'Waiting for you',
  transferring: 'Transferring',
  paused: 'Paused',
  completed: 'Completed',
  failed: 'Failed',
  cancelled: 'Cancelled'
};

function renderTransfer(transfer) {
  if (!transfer) {
    els.transferProgressPanel.classList.add('hidden');
    updateSendButton();
    return;
  }

  els.transferProgressPanel.classList.remove('hidden');
  const terminal = isTerminalPhase(transfer.phase);
  const direction = transfer.direction === 'incoming' ? 'Receiving from' : 'Sending to';

  els.transferProgressTitle.textContent = terminal
    ? phaseLabels[transfer.phase]
    : `${direction} ${transfer.peerName}`;
  els.transferProgressPeer.textContent = `${transfer.fileCount} file(s) · ${formatBytes(transfer.totalSize)}`;
  els.transferPhaseBadge.textContent = phaseLabels[transfer.phase] || transfer.phase;
  els.transferPhaseBadge.className = `badge ${transfer.phase}`;

  const overallPct = transfer.totalSize > 0
    ? Math.min(100, Math.round((transfer.transferredBytes / transfer.totalSize) * 100))
    : (transfer.phase === 'completed' ? 100 : 0);
  els.transferOverallFill.style.width = `${overallPct}%`;
  els.transferOverallText.textContent = `${overallPct}% · ${formatBytes(transfer.transferredBytes)} of ${formatBytes(transfer.totalSize)}`;
  els.transferSpeed.textContent = formatSpeed(transfer.speedBps);
  els.transferEta.textContent = terminal ? '' : formatEta(transfer.etaSeconds);

  if (transfer.currentFileName && !terminal) {
    els.transferFileName.textContent = transfer.currentFileName;
    const filePct = transfer.currentFileSize > 0
      ? Math.min(100, Math.round((transfer.currentFileBytes / transfer.currentFileSize) * 100))
      : 0;
    els.transferFileFill.style.width = `${filePct}%`;
    els.transferFileText.textContent = `${formatBytes(transfer.currentFileBytes)} of ${formatBytes(transfer.currentFileSize)}`;
    els.transferCounter.textContent = `File ${Math.min(transfer.currentIndex + 1, transfer.fileCount)} of ${transfer.fileCount}`;
  } else {
    els.transferFileName.textContent = '';
    els.transferFileFill.style.width = terminal && transfer.phase === 'completed' ? '100%' : '0%';
    els.transferFileText.textContent = '';
    els.transferCounter.textContent = `${transfer.completedFiles} of ${transfer.fileCount} files done`;
  }

  els.transferMessage.textContent = transfer.message || '';

  const transferring = transfer.phase === 'transferring';
  const paused = transfer.phase === 'paused';
  els.transferPause.classList.toggle('hidden', !transferring);
  els.transferResume.classList.toggle('hidden', !paused);
  els.transferCancel.classList.toggle('hidden', terminal);
  els.transferRetry.classList.toggle(
    'hidden',
    !(terminal && transfer.direction === 'outgoing' && transfer.phase !== 'completed')
  );
  els.transferDone.classList.toggle('hidden', !terminal);
  updateSendButton();
}

function renderTransferHistory(records) {
  if (!records.length) {
    els.transferHistoryList.innerHTML = '<div class="empty-state">No transfers yet.</div>';
    return;
  }

  els.transferHistoryList.innerHTML = records.map((record) => `
    <div class="list-item">
      <div>
        <div class="item-title">${record.direction === 'incoming' ? '↓ From' : '↑ To'} ${escapeHtml(record.deviceName || 'Unknown')}</div>
        <div class="item-meta">${record.fileCount} file(s) · ${formatBytes(record.totalSize)} · ${formatTime(record.createdAt)}</div>
        ${record.message ? `<div class="item-meta">${escapeHtml(record.message)}</div>` : ''}
      </div>
      <span class="badge ${escapeAttr(record.status)}">${escapeHtml(record.status)}</span>
    </div>
  `).join('');
}

// ── staging files (picker + drag & drop) ────────────────────────────────────

async function stagePaths(paths) {
  const fresh = (paths || []).filter((path) => path && !stagedFiles.some((file) => file.path === path));
  if (!fresh.length) {
    return 0;
  }
  const files = await window.syncMesh.statTransferFiles(fresh);
  if (files.length) {
    stagedFiles = stagedFiles.concat(files);
    renderStagedFiles();
    setActiveView('transfer');
  }
  return files.length;
}

async function stageDroppedFiles(fileList) {
  const paths = [];
  for (const file of fileList) {
    try {
      const path = window.syncMesh.getPathForFile(file);
      if (path) {
        paths.push(path);
      }
    } catch (_) {}
  }
  if (!(await stagePaths(paths))) {
    showToast('Could not read the dropped files (folders are not supported yet)');
  }
}

// ── incoming transfer modal ──────────────────────────────────────────────────

function showIncomingModal(offer) {
  incomingOffer = offer;
  els.incomingSummary.textContent =
    `${offer.senderName} wants to send ${offer.fileCount} file(s) (${formatBytes(offer.totalSize)}).`;
  els.incomingFiles.innerHTML = (offer.files || []).map((file) => `
    <div class="incoming-file">
      <span>${escapeHtml(file.name)}</span>
      <span class="size">${formatBytes(file.size)}</span>
    </div>
  `).join('');
  els.incomingDest.textContent = state?.transferSaveDir
    ? `Files will be saved to ${prettyPath(state.transferSaveDir)}`
    : '';
  els.incomingModal.classList.remove('hidden');
  setActiveView('transfer');
}

function hideIncomingModal() {
  incomingOffer = null;
  els.incomingModal.classList.add('hidden');
}

async function respondToIncoming(accepted) {
  if (!incomingOffer) {
    return;
  }
  const transferId = incomingOffer.transferId;
  hideIncomingModal();
  try {
    await window.syncMesh.respondTransfer({ transferId, accepted });
  } catch (error) {
    showToast(error.message);
  }
}

// ── wiring ───────────────────────────────────────────────────────────────────

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

    render(await window.syncMesh.pairManual({ ipAddress, port, pairingCode }));
    els.pairCode.value = '';
    showToast('Device paired');
  } catch (error) {
    showToast(error.message);
  }
});

document.body.addEventListener('click', async (event) => {
  const removeButton = event.target.closest('.remove-device');
  if (removeButton) {
    try {
      render(await window.syncMesh.removeDevice(removeButton.dataset.deviceId));
      showToast('Device removed');
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  const nearbyButton = event.target.closest('.nearby-pair');
  if (nearbyButton) {
    setActiveView('pair');
    els.pairIp.value = nearbyButton.dataset.ip;
    els.pairPort.value = nearbyButton.dataset.port || 8989;
    showToast('Nearby device IP copied to pairing form');
    return;
  }

  const removeStaged = event.target.closest('.staged-file .remove');
  if (removeStaged) {
    stagedFiles.splice(Number(removeStaged.dataset.index), 1);
    renderStagedFiles();
  }
});

// ── transfer wiring ──────────────────────────────────────────────────────────

els.chooseFiles.addEventListener('click', async () => {
  try {
    await stagePaths(await window.syncMesh.pickTransferFiles());
  } catch (error) {
    showToast(error.message);
  }
});

els.clearFiles.addEventListener('click', () => {
  stagedFiles = [];
  renderStagedFiles();
});

els.goPair.addEventListener('click', () => setActiveView('pair'));

els.openTransferFolder.addEventListener('click', () => {
  window.syncMesh.openTransferFolder().catch((error) => showToast(error.message));
});

els.chooseTransferFolder.addEventListener('click', async () => {
  try {
    const nextState = await window.syncMesh.chooseTransferFolder();
    if (nextState) {
      render(nextState);
      showToast(`Received files will be saved to ${prettyPath(nextState.transferSaveDir)}`);
    }
  } catch (error) {
    showToast(error.message);
  }
});

els.resetTransferFolder.addEventListener('click', async () => {
  try {
    render(await window.syncMesh.resetTransferFolder());
    showToast('Save location reset to the default folder');
  } catch (error) {
    showToast(error.message);
  }
});

els.sendFiles.addEventListener('click', async () => {
  try {
    if (!state?.running) {
      throw new Error('Start sync before sending files');
    }
    const deviceId = els.transferDevice.value;
    if (!deviceId) {
      throw new Error('Choose a paired device first');
    }
    await window.syncMesh.sendTransfer({
      deviceId,
      filePaths: stagedFiles.map((file) => file.path)
    });
    stagedFiles = [];
    renderStagedFiles();
  } catch (error) {
    showToast(error.message);
  }
});

els.transferPause.addEventListener('click', () => window.syncMesh.pauseTransfer());
els.transferResume.addEventListener('click', () => window.syncMesh.resumeTransfer());
els.transferCancel.addEventListener('click', () => window.syncMesh.cancelTransfer());
els.transferRetry.addEventListener('click', async () => {
  try {
    await window.syncMesh.retryTransfer();
  } catch (error) {
    showToast(error.message);
  }
});
els.transferDone.addEventListener('click', async () => {
  await window.syncMesh.dismissTransfer();
  refreshState();
});

els.incomingAccept.addEventListener('click', () => respondToIncoming(true));
els.incomingReject.addEventListener('click', () => respondToIncoming(false));

// Drag & drop: works on the dedicated drop zone and anywhere in the window.
window.addEventListener('dragenter', (event) => {
  if (!event.dataTransfer?.types?.includes('Files')) {
    return;
  }
  dragDepth += 1;
  els.dragOverlay.classList.remove('hidden');
  els.dropZone.classList.add('drag-over');
});

window.addEventListener('dragleave', () => {
  dragDepth = Math.max(0, dragDepth - 1);
  if (dragDepth === 0) {
    els.dragOverlay.classList.add('hidden');
    els.dropZone.classList.remove('drag-over');
  }
});

window.addEventListener('dragover', (event) => {
  event.preventDefault();
});

window.addEventListener('drop', (event) => {
  event.preventDefault();
  dragDepth = 0;
  els.dragOverlay.classList.add('hidden');
  els.dropZone.classList.remove('drag-over');
  if (event.dataTransfer?.files?.length) {
    stageDroppedFiles(event.dataTransfer.files);
  }
});

// ── push events ──────────────────────────────────────────────────────────────

window.syncMesh.onState((payload) => render(payload));
window.syncMesh.onLog((log) => {
  if (!state) {
    return;
  }
  state.logs = [log, ...(state.logs || [])].slice(0, 300);
  renderLogs(state.logs);
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
  state.nearbyDevices = nearbyDevices;
  renderNearby(nearbyDevices);
});
window.syncMesh.onTransferState((transfer) => {
  if (state) {
    state.transfer = transfer;
  }
  renderTransfer(transfer);
});
window.syncMesh.onTransferIncoming((offer) => showIncomingModal(offer));
window.syncMesh.onTransferFinished(({ status }) => {
  if (incomingOffer) {
    hideIncomingModal();
  }
  showToast(status === 'completed' ? 'Transfer completed' : `Transfer ${status}`);
  refreshState();
});
window.syncMesh.onDeviceUnpaired(({ deviceName }) => {
  showToast(`${deviceName} removed the pairing`);
  refreshState();
});

// ── app settings toggles ─────────────────────────────────────────────────────

document.body.classList.add(`platform-${window.syncMesh.platform}`);

async function saveToggle(key, value, message) {
  try {
    render(await window.syncMesh.updateSettings({ [key]: value }));
    showToast(message);
  } catch (error) {
    showToast(error.message);
  }
}

els.launchAtLogin.addEventListener('change', () => {
  saveToggle('launchAtLogin', els.launchAtLogin.checked,
    els.launchAtLogin.checked ? 'SyncMesh will launch at login' : 'Launch at login disabled');
});

els.startMinimized.addEventListener('change', () => {
  saveToggle('startMinimized', els.startMinimized.checked,
    els.startMinimized.checked ? 'SyncMesh will start minimized in the tray' : 'SyncMesh will open its window on start');
});

els.notificationsEnabled.addEventListener('change', () => {
  saveToggle('notificationsEnabled', els.notificationsEnabled.checked,
    els.notificationsEnabled.checked ? 'Notifications enabled' : 'Notifications disabled');
});

// ── theme (dark / light) ─────────────────────────────────────────────────────

function applyTheme(theme) {
  const value = theme === 'dark' ? 'dark' : 'light';
  document.documentElement.dataset.theme = value;
  els.themeToggleIcon.textContent = value === 'dark' ? '☀' : '☾';
  els.themeToggleLabel.textContent = value === 'dark' ? 'Light Mode' : 'Dark Mode';
  localStorage.setItem('syncmesh-theme', value);
  window.syncMesh.setTheme(value).catch(() => {});
}

els.themeToggle.addEventListener('click', () => {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  window.syncMesh.updateSettings({ theme: next }).catch(() => {});
});

applyTheme(localStorage.getItem('syncmesh-theme') || 'light');

// ── debug easter egg (7 clicks on the version label, like the Android app) ──

let versionTaps = 0;
let versionTapTimer = null;

els.versionLabel.addEventListener('click', async () => {
  if (state?.debugUnlocked) {
    return;
  }
  clearTimeout(versionTapTimer);
  versionTapTimer = setTimeout(() => { versionTaps = 0; }, 2000);
  versionTaps += 1;
  if (versionTaps >= 7) {
    versionTaps = 0;
    try {
      render(await window.syncMesh.setDebugUnlocked(true));
      showToast('Debug mode unlocked');
    } catch (error) {
      showToast(error.message);
    }
  } else if (versionTaps >= 4) {
    showToast(`${7 - versionTaps} more taps to unlock debug mode`);
  }
});

els.debugOff.addEventListener('click', async () => {
  try {
    render(await window.syncMesh.setDebugUnlocked(false));
    setActiveView('home');
    showToast('Debug mode disabled');
  } catch (error) {
    showToast(error.message);
  }
});

// ── clear buttons ────────────────────────────────────────────────────────────

els.clearHistory.addEventListener('click', async () => {
  try {
    render(await window.syncMesh.clearHistory());
    showToast('Clipboard history cleared');
  } catch (error) {
    showToast(error.message);
  }
});

els.clearTransferHistoryBtn.addEventListener('click', async () => {
  try {
    render(await window.syncMesh.clearTransferHistory());
    showToast('Transfer history cleared');
  } catch (error) {
    showToast(error.message);
  }
});

els.clearLogs.addEventListener('click', async () => {
  try {
    render(await window.syncMesh.clearLogs());
    showToast('Logs cleared');
  } catch (error) {
    showToast(error.message);
  }
});

els.copyLogs.addEventListener('click', async () => {
  const text = (state?.logs || [])
    .map((item) => `[${item.level}] ${formatTime(item.createdAt)} ${item.message}`)
    .join('\n');
  if (!text) {
    showToast('No logs to copy');
    return;
  }
  await navigator.clipboard.writeText(text);
  showToast('Logs copied to clipboard');
});

// ── onboarding carousel (first run, like the Android app) ───────────────────

const onboardingSlides = [
  {
    icon: '👋',
    title: 'Welcome to SyncMesh',
    text: 'Sync your clipboard and share files between this desktop and your Android phone — over your own WiFi, with no cloud and no account.'
  },
  {
    icon: '📋',
    title: 'Clipboard, everywhere',
    text: 'Copy text on one device and paste it on another. While sync is running, everything you copy here is sent to your paired Android devices instantly.'
  },
  {
    icon: '🔗',
    title: 'Pair once, sync forever',
    text: 'Pair with a 6-digit code — type the Android device\'s IP manually, scan the QR from your phone, or tap a device discovered nearby.'
  },
  {
    icon: '📁',
    title: 'Send files too',
    text: 'Drag & drop any files into the window (up to 2 GB each) and send them to a paired device. Incoming files land in Downloads/SyncMesh.'
  },
  {
    icon: '🔒',
    title: 'Private by design',
    text: 'No account, no cloud, no analytics. Data moves directly between your devices on the local network, and history is stored only on this computer.'
  },
  {
    icon: '🚀',
    title: 'You\'re all set',
    text: 'Hit Start Sync, pair your phone, and copy something. A quick tour will show you around after this.'
  }
];

let onboardingIndex = 0;
let onboardingShown = false;

function renderOnboardingSlide() {
  const slide = onboardingSlides[onboardingIndex];
  els.onboardingIcon.textContent = slide.icon;
  els.onboardingTitle.textContent = slide.title;
  els.onboardingText.textContent = slide.text;
  els.onboardingDots.innerHTML = onboardingSlides
    .map((_, i) => `<span class="onboarding-dot${i === onboardingIndex ? ' active' : ''}"></span>`)
    .join('');
  els.onboardingBack.style.visibility = onboardingIndex === 0 ? 'hidden' : 'visible';
  els.onboardingNext.textContent = onboardingIndex === onboardingSlides.length - 1 ? 'Get Started' : 'Next';
  els.onboardingSkip.style.visibility = onboardingIndex === onboardingSlides.length - 1 ? 'hidden' : 'visible';
}

function startOnboarding() {
  onboardingIndex = 0;
  renderOnboardingSlide();
  els.onboarding.classList.remove('hidden');
}

async function finishOnboarding(runTour) {
  els.onboarding.classList.add('hidden');
  try {
    await window.syncMesh.updateSettings({ onboardingComplete: true });
  } catch (_) {}
  if (runTour) {
    startTour();
  }
}

els.onboardingNext.addEventListener('click', () => {
  if (onboardingIndex === onboardingSlides.length - 1) {
    finishOnboarding(true);
  } else {
    onboardingIndex += 1;
    renderOnboardingSlide();
  }
});

els.onboardingBack.addEventListener('click', () => {
  if (onboardingIndex > 0) {
    onboardingIndex -= 1;
    renderOnboardingSlide();
  }
});

els.onboardingSkip.addEventListener('click', () => finishOnboarding(false));

// ── guided spotlight tour ────────────────────────────────────────────────────

const tourSteps = [
  {
    target: '#syncToggle',
    title: 'Start syncing',
    text: 'This starts the sync service — clipboard, discovery and file transfer all come alive with one click.'
  },
  {
    target: '.nav-item[data-view="pair"]',
    title: 'Pair a device',
    text: 'Connect your Android phone here using its IP and 6-digit pairing code, the QR code, or nearby discovery.'
  },
  {
    target: '.nav-item[data-view="devices"]',
    title: 'Your paired devices',
    text: 'Everything you\'ve paired lives here. Only these devices can send you clipboard text and files.'
  },
  {
    target: '.nav-item[data-view="transfer"]',
    title: 'File transfer',
    text: 'Send files to a paired device — or just drag & drop them anywhere in this window.'
  },
  {
    target: '.nav-item[data-view="history"]',
    title: 'Clipboard history',
    text: 'Every synced clip is saved locally here, incoming and outgoing.'
  },
  {
    target: '#sidebarStatus',
    title: 'Sync status',
    text: 'Keep an eye on this pill — green means the service is running and your devices can reach you.'
  }
];

let tourIndex = -1;

function startTour() {
  tourIndex = 0;
  els.tourOverlay.classList.remove('hidden');
  positionTourStep();
}

function endTour() {
  tourIndex = -1;
  els.tourOverlay.classList.add('hidden');
}

function positionTourStep() {
  const step = tourSteps[tourIndex];
  const target = document.querySelector(step.target);
  if (!target) {
    advanceTour();
    return;
  }
  const rect = target.getBoundingClientRect();
  const pad = 6;
  els.tourHighlight.style.left = `${rect.left - pad}px`;
  els.tourHighlight.style.top = `${rect.top - pad}px`;
  els.tourHighlight.style.width = `${rect.width + pad * 2}px`;
  els.tourHighlight.style.height = `${rect.height + pad * 2}px`;

  els.tourTitle.textContent = step.title;
  els.tourText.textContent = step.text;
  els.tourStepCount.textContent = `${tourIndex + 1} of ${tourSteps.length}`;
  els.tourNext.textContent = tourIndex === tourSteps.length - 1 ? 'Done' : 'Next';

  // Place the card to the right of the target when there's room, else below.
  const cardWidth = 320;
  const cardHeight = els.tourCard.offsetHeight || 140;
  let left = rect.right + 16;
  let top = rect.top;
  if (left + cardWidth > window.innerWidth - 16) {
    left = Math.min(rect.left, window.innerWidth - cardWidth - 16);
    top = rect.bottom + 14;
  }
  if (top + cardHeight > window.innerHeight - 16) {
    top = Math.max(16, window.innerHeight - cardHeight - 16);
  }
  els.tourCard.style.left = `${Math.max(16, left)}px`;
  els.tourCard.style.top = `${Math.max(16, top)}px`;
}

function advanceTour() {
  if (tourIndex >= tourSteps.length - 1) {
    endTour();
    return;
  }
  tourIndex += 1;
  positionTourStep();
}

els.tourNext.addEventListener('click', advanceTour);
els.tourSkip.addEventListener('click', endTour);
window.addEventListener('resize', () => {
  if (tourIndex >= 0) {
    positionTourStep();
  }
});

// ── init ─────────────────────────────────────────────────────────────────────

setActiveView(activeView);
renderStagedFiles();
refreshState()
  .then(() => {
    if (!onboardingShown && state?.settings?.onboardingComplete !== 'true') {
      onboardingShown = true;
      startOnboarding();
    }
  })
  .catch((error) => showToast(error.message));
