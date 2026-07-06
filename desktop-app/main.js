const { app, BrowserWindow, dialog, ipcMain, nativeImage, nativeTheme, Notification, shell } = require('electron');
const fs = require('fs');
const path = require('path');
const log = require('electron-log');

const Database = require('./src/database');
const SyncController = require('./src/syncController');
const { createTray } = require('./src/tray');
const { safeNumber, safeString } = require('./src/safe');

let mainWindow;
let tray;
let database;
let syncController;

process.on('uncaughtException', (error) => {
  log.error('UNCAUGHT_EXCEPTION', error);
});

process.on('unhandledRejection', (reason) => {
  log.error('UNHANDLED_REJECTION', reason);
});

function createWindow() {
  const isMac = process.platform === 'darwin';
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 780,
    minWidth: 920,
    minHeight: 640,
    show: false,
    title: 'SyncMesh Desktop',
    icon: path.join(__dirname, 'assets', 'icon-512.png'),
    backgroundColor: '#00000000',
    // macOS-native chrome: content flows under a hidden title bar with inset
    // traffic lights, and the sidebar picks up window vibrancy.
    ...(isMac
      ? {
          titleBarStyle: 'hiddenInset',
          trafficLightPosition: { x: 18, y: 18 },
          vibrancy: 'sidebar',
          visualEffectState: 'active'
        }
      : process.platform === 'win32'
        ? {
            // Frameless with native window-control overlay so the titlebar
            // follows the app theme instead of staying system-light.
            titleBarStyle: 'hidden',
            titleBarOverlay: titleBarOverlayColors(nativeTheme.themeSource === 'dark'),
            backgroundColor: '#f5f5f7'
          }
        : { backgroundColor: '#f5f5f7' }),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  const startMinimized = database.getSetting('startMinimized') === 'true';
  mainWindow.once('ready-to-show', () => {
    if (!startMinimized) {
      mainWindow.show();
    }
  });

  mainWindow.on('close', (event) => {
    if (!app.isQuiting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });
}

function sendToRenderer(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload);
    if (channel === 'transfer:incoming') {
      mainWindow.show();
      mainWindow.focus();
    }
  }
  maybeNotify(channel, payload);
}

function maybeNotify(channel, payload) {
  if (channel === 'clipboard:received') {
    notify(`Clipboard from ${payload.deviceName}`, payload.preview || '');
  } else if (channel === 'transfer:incoming') {
    notify(
      `Incoming files from ${payload.senderName}`,
      `${payload.fileCount} file(s), ${formatBytes(payload.totalSize)} — click to accept or reject`
    );
  } else if (channel === 'transfer:finished') {
    const who = payload.direction === 'incoming' ? `from ${payload.peerName}` : `to ${payload.peerName}`;
    const what = `${payload.fileCount} file(s) ${who}`;
    if (payload.status === 'completed') {
      notify('Transfer completed', what);
    } else if (payload.status === 'failed') {
      notify('Transfer failed', what);
    }
  } else if (channel === 'device:unpaired') {
    notify('Device unpaired', `${payload.deviceName} removed the pairing`);
  }
}

function notify(title, body) {
  if (!Notification.isSupported() || !database) {
    return;
  }
  if (database.getSetting('notificationsEnabled', 'true') !== 'true') {
    return;
  }
  // Don't nag while the user is already looking at the app.
  if (mainWindow && !mainWindow.isDestroyed() && mainWindow.isVisible() && mainWindow.isFocused()) {
    return;
  }
  try {
    const notification = new Notification({ title, body });
    notification.on('click', () => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.show();
        mainWindow.focus();
      }
    });
    notification.show();
  } catch (error) {
    log.warn(`NOTIFY_FAILED ${error.message}`);
  }
}

function formatBytes(bytes) {
  const value = Number(bytes) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function titleBarOverlayColors(dark) {
  return dark
    ? { color: '#1c1c1e', symbolColor: '#f5f5f7', height: 36 }
    : { color: '#f5f5f7', symbolColor: '#1d1d1f', height: 36 };
}

function applyLoginItemSettings() {
  const openAtLogin = database.getSetting('launchAtLogin') === 'true';
  const openAsHidden = database.getSetting('startMinimized') === 'true';
  try {
    app.setLoginItemSettings({ openAtLogin, openAsHidden });
  } catch (error) {
    log.warn(`LOGIN_ITEM_SETTINGS_FAILED ${error.message}`);
  }
}

async function bootstrap() {
  app.setName('SyncMesh Desktop');

  database = new Database(app.getPath('userData'));
  await database.initialize();

  const savedTheme = database.getSetting('theme', 'light');
  nativeTheme.themeSource = savedTheme === 'dark' ? 'dark' : 'light';

  syncController = new SyncController({
    database,
    log,
    emit: sendToRenderer,
    transferSaveDir: path.join(app.getPath('downloads'), 'SyncMesh')
  });

  createWindow();

  tray = createTray({
    app,
    window: mainWindow,
    controller: syncController,
    getStatus: () => syncController.getStatus(),
    icon: createTrayIcon()
  });

  registerIpc();
  applyLoginItemSettings();
  await syncController.start();
}

function createTrayIcon() {
  const logo = nativeImage.createFromPath(path.join(__dirname, 'assets', 'icon-512.png'));
  return logo.resize({ width: 20, height: 20, quality: 'best' });
}

function registerIpc() {
  ipcMain.handle('app:get-state', async () => syncController.getState());
  ipcMain.handle('sync:start', async () => syncController.start());
  ipcMain.handle('sync:stop', async () => syncController.stop());
  ipcMain.handle('settings:update', async (_event, settings) => {
    const nextState = await syncController.updateSettings(settings);
    if (settings?.launchAtLogin !== undefined || settings?.startMinimized !== undefined) {
      applyLoginItemSettings();
    }
    return nextState;
  });
  ipcMain.handle('devices:list', async () => database.listDevices());
  ipcMain.handle('devices:remove', async (_event, deviceId) => syncController.removeDeviceAndNotify(safeString(deviceId)));
  ipcMain.handle('history:list', async (_event, limit) => database.listClipboardHistory(limit));
  ipcMain.handle('logs:list', async () => syncController.getLogs());
  ipcMain.handle('logs:clear', async () => syncController.clearLogs());
  ipcMain.handle('history:clear', async () => syncController.clearClipboardHistory());
  ipcMain.handle('transfer:clear-history', async () => syncController.clearTransferHistory());
  ipcMain.handle('debug:set-unlocked', async (_event, unlocked) => syncController.setDebugUnlocked(Boolean(unlocked)));
  ipcMain.handle('app:set-theme', async (_event, theme) => {
    // Keeps the macOS vibrancy material and system chrome in step with the UI theme.
    const dark = theme === 'dark';
    nativeTheme.themeSource = dark ? 'dark' : theme === 'light' ? 'light' : 'system';
    if (process.platform === 'win32' && mainWindow && !mainWindow.isDestroyed()) {
      try {
        mainWindow.setTitleBarOverlay(titleBarOverlayColors(dark));
      } catch (_) {
        // overlay not available on this frame style
      }
    }
  });
  ipcMain.handle('pair:manual', async (_event, payload) => {
    const safePayload = {
      ipAddress: safeString(payload?.ipAddress).trim(),
      port: safeNumber(payload?.port, 8989),
      pairingCode: safeString(payload?.pairingCode).trim()
    };
    syncController.addLog('info', `IPC_PAIR_MANUAL_PAYLOAD ${JSON.stringify({
      ipAddress: safePayload.ipAddress,
      port: safePayload.port,
      pairingCode: safePayload.pairingCode ? 'provided' : ''
    })}`);
    if (!safePayload.ipAddress) {
      throw new Error('Android IP address is required');
    }
    if (!safePayload.pairingCode) {
      throw new Error('Pairing code is required');
    }
    return syncController.pairManual(safePayload);
  });
  ipcMain.handle('pair:accept', async (_event, payload) => syncController.acceptPairing(payload));
  ipcMain.handle('pair:reject', async (_event, payload) => syncController.rejectPairing(payload));
  ipcMain.handle('discovery:list', async () => syncController.getNearbyDevices());
  ipcMain.handle('clipboard:send-current', async () => syncController.sendCurrentClipboard());

  // ── file transfer ──────────────────────────────────────────────────────────
  ipcMain.handle('transfer:pick-files', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Choose files to send',
      properties: ['openFile', 'multiSelections']
    });
    return result.canceled ? [] : result.filePaths;
  });
  ipcMain.handle('transfer:send', async (_event, payload) => {
    const deviceId = safeString(payload?.deviceId);
    const filePaths = Array.isArray(payload?.filePaths) ? payload.filePaths.map(safeString) : [];
    const device = database.getDevice(deviceId);
    if (!device) {
      throw new Error('Choose a paired device first');
    }
    if (!filePaths.length) {
      throw new Error('No files selected');
    }
    return syncController.transfers.sendFiles(device, filePaths);
  });
  ipcMain.handle('transfer:respond', async (_event, payload) => {
    syncController.transfers.respondToIncoming(safeString(payload?.transferId), Boolean(payload?.accepted));
  });
  ipcMain.handle('transfer:pause', async () => syncController.transfers.pause());
  ipcMain.handle('transfer:resume', async () => syncController.transfers.resume());
  ipcMain.handle('transfer:cancel', async () => syncController.transfers.cancel());
  ipcMain.handle('transfer:retry', async () => syncController.transfers.retryLast());
  ipcMain.handle('transfer:dismiss', async () => syncController.transfers.dismiss());
  ipcMain.handle('transfer:state', async () => syncController.transfers.getState());
  ipcMain.handle('transfer:history', async (_event, limit) => database.listTransferHistory(limit));
  ipcMain.handle('transfer:open-folder', async () => {
    const dir = syncController.transfers.saveDir;
    fs.mkdirSync(dir, { recursive: true });
    await shell.openPath(dir);
  });
  ipcMain.handle('transfer:choose-folder', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Choose where received files are saved',
      defaultPath: syncController.transfers.saveDir,
      properties: ['openDirectory', 'createDirectory']
    });
    if (result.canceled || !result.filePaths.length) {
      return null;
    }
    return syncController.setTransferSaveDir(result.filePaths[0]);
  });
  ipcMain.handle('transfer:reset-folder', async () => syncController.setTransferSaveDir(null));
  ipcMain.handle('transfer:stat-files', async (_event, filePaths) => {
    const paths = Array.isArray(filePaths) ? filePaths.map(safeString) : [];
    const files = [];
    for (const filePath of paths) {
      try {
        const stats = fs.statSync(filePath);
        if (stats.isFile()) {
          files.push({ path: filePath, name: path.basename(filePath), size: stats.size });
        }
      } catch (_) {
        // unreadable path — skip it
      }
    }
    return files;
  });
}

app.whenReady().then(bootstrap);

app.on('window-all-closed', (event) => {
  event.preventDefault();
});

app.on('before-quit', async () => {
  app.isQuiting = true;
  if (syncController) {
    await syncController.stop();
  }
  if (database) {
    database.close();
  }
});

app.on('activate', () => {
  if (!mainWindow) {
    createWindow();
  } else {
    mainWindow.show();
  }
});
