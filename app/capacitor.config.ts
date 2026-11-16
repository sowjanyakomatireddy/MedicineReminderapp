import type { CapacitorConfig } from '@capacitor/cli';

/**
 * The elder's Android app: the same React build, wrapped by Capacitor, plus the native MedAlarm plugin
 * (android/app/src/main/java/app/medreminder/alarm). The app opens on the /elder screens.
 */
const config: CapacitorConfig = {
  appId: 'app.medreminder',
  appName: 'Medicine Reminder',
  webDir: 'dist',
  plugins: {
    // Send API calls through Android's native HTTP stack: no CORS, and the backend can be on the home network.
    CapacitorHttp: { enabled: true },
  },
};

export default config;
