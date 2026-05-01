const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('syncMesh', {
  getState: () => ipcRenderer.invoke('app:get-state'),
  startSync: () => ipcRenderer.invoke('sync:start'),
  stopSync: () => ipcRenderer.invoke('sync:stop'),
  updateSettings: (settings) => ipcRenderer.invoke('settings:update', settings),
  listDevices: () => ipcRenderer.invoke('devices:list'),
  removeDevice: (deviceId) => ipcRenderer.invoke('devices:remove', deviceId),
  listHistory: (limit) => ipcRenderer.invoke('history:list', limit),
  listLogs: () => ipcRenderer.invoke('logs:list'),
  pairManual: (payload) => ipcRenderer.invoke('pair:manual', payload),
  acceptPairing: (payload) => ipcRenderer.invoke('pair:accept', payload),
  rejectPairing: (payload) => ipcRenderer.invoke('pair:reject', payload),
  listNearby: () => ipcRenderer.invoke('discovery:list'),
  sendCurrentClipboard: () => ipcRenderer.invoke('clipboard:send-current'),
  onState: (callback) => ipcRenderer.on('state:update', (_event, payload) => callback(payload)),
  onLog: (callback) => ipcRenderer.on('log:new', (_event, payload) => callback(payload)),
  onPairRequest: (callback) => ipcRenderer.on('pair:request', (_event, payload) => callback(payload)),
  onHistory: (callback) => ipcRenderer.on('history:new', (_event, payload) => callback(payload)),
  onNearby: (callback) => ipcRenderer.on('nearby:update', (_event, payload) => callback(payload))
});
