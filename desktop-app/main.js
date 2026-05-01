const { app, BrowserWindow, ipcMain, nativeImage } = require('electron');
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
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 780,
    minWidth: 920,
    minHeight: 640,
    show: false,
    title: 'SyncMesh Desktop',
    backgroundColor: '#f7f8fb',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
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
  }
}

async function bootstrap() {
  app.setName('SyncMesh Desktop');

  database = new Database(app.getPath('userData'));
  await database.initialize();

  syncController = new SyncController({
    database,
    log,
    emit: sendToRenderer
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
  await syncController.start();
}

function createTrayIcon() {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
      <rect width="32" height="32" rx="7" fill="#0f766e"/>
      <path d="M9 11.5C9 8.9 11.1 7 14.1 7h7.1v4h-7.1c-.7 0-1.1.3-1.1.8 0 .7.7.9 2.9 1.4 3.4.7 6.4 1.7 6.4 5.6 0 3.8-2.9 6.2-7.2 6.2H9v-4h6.2c1.7 0 2.8-.7 2.8-1.9 0-1.1-.9-1.4-3.4-2-3-.7-5.6-1.7-5.6-5.6Z" fill="#fff"/>
    </svg>
  `;
  return nativeImage.createFromDataURL(`data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`);
}

function registerIpc() {
  ipcMain.handle('app:get-state', async () => syncController.getState());
  ipcMain.handle('sync:start', async () => syncController.start());
  ipcMain.handle('sync:stop', async () => syncController.stop());
  ipcMain.handle('settings:update', async (_event, settings) => syncController.updateSettings(settings));
  ipcMain.handle('devices:list', async () => database.listDevices());
  ipcMain.handle('devices:remove', async (_event, deviceId) => database.removeDevice(deviceId));
  ipcMain.handle('history:list', async (_event, limit) => database.listClipboardHistory(limit));
  ipcMain.handle('logs:list', async () => syncController.getLogs());
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
