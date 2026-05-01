const { Menu, Tray } = require('electron');

function createTray({ app, window, controller, getStatus, icon }) {
  const tray = new Tray(icon);
  tray.setToolTip('SyncMesh Desktop');

  const updateMenu = () => {
    const status = getStatus();
    const isRunning = status.running;

    tray.setContextMenu(Menu.buildFromTemplate([
      {
        label: isRunning ? 'Stop Sync' : 'Start Sync',
        click: () => {
          if (isRunning) {
            controller.stop();
          } else {
            controller.start();
          }
          setTimeout(updateMenu, 200);
        }
      },
      {
        label: 'Open Dashboard',
        click: () => {
          window.show();
          window.focus();
        }
      },
      { type: 'separator' },
      {
        label: 'Quit',
        click: () => {
          app.isQuiting = true;
          app.quit();
        }
      }
    ]));
  };

  tray.on('click', () => {
    window.isVisible() ? window.hide() : window.show();
  });

  updateMenu();
  controller.onStatusChange = updateMenu;
  return tray;
}

module.exports = {
  createTray
};
