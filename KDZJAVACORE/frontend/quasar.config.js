const { configure } = require('quasar/wrappers');

module.exports = configure(function () {
  return {
    boot: ['pinia', 'axios'],
    css: ['app.scss'],
    extras: ['roboto-font', 'material-icons'],
    build: {
      target: {
        browser: ['es2019', 'edge88', 'firefox78', 'chrome87', 'safari13.1'],
        node: 'node16',
      },
      vitePlugins: [],
    },
    devServer: {
      port: 9000,
      open: true,
    },
    framework: {
      config: {},
      plugins: ['Dialog', 'Notify'],
    },
    animations: [],
    ssr: {
      pwa: false,
      prodPort: 3000,
      middlewares: ['render'],
    },
    pwa: {
      workboxMode: 'generateSW',
    },
    cordova: {},
    capacitor: {},
    electron: {},
    bex: {},
  };
});
