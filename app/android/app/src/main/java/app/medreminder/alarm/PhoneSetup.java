package app.medreminder.alarm;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSObject;

import java.util.Locale;

/**
 * The "Set up this phone" checklist: what is already allowed, and the right settings page for each step.
 * Phone makers (Xiaomi, Oppo, Vivo, Samsung, Huawei, OnePlus...) add their own "autostart" or battery screens
 * that can stop alarms; we open them where we know the page, else fall back to the app's settings.
 */
final class PhoneSetup {
    /** Known autostart / background pages, tried in order. Package and activity names change between versions. */
    private static final String[][] AUTOSTART = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
            {"com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
    };

    private PhoneSetup() {
    }

    static JSObject status(Context ctx) {
        JSObject s = new JSObject();
        s.put("manufacturer", Build.MANUFACTURER);
        s.put("model", Build.MODEL);
        s.put("sdk", Build.VERSION.SDK_INT);
        s.put("notifications", NotificationManagerCompat.from(ctx).areNotificationsEnabled());
        s.put("exactAlarms", AlarmScheduler.canScheduleExact(ctx));
        s.put("fullScreen", canFullScreen(ctx));
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        s.put("batteryUnrestricted", pm.isIgnoringBatteryOptimizations(ctx.getPackageName()));
        s.put("autostartPage", autostartIntent(ctx) != null);
        s.put("xiaomiLockScreenPage", isXiaomi() && xiaomiPermissionsIntent(ctx) != null);
        return s;
    }

    static boolean canFullScreen(Context ctx) {
        if (Build.VERSION.SDK_INT < 34) {
            return true; // granted at install before Android 14
        }
        return ctx.getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }

    /** Opens the settings page for one checklist step. Returns false if nothing could be opened. */
    static boolean open(Context ctx, String target) {
        Uri pkg = Uri.parse("package:" + ctx.getPackageName());
        Intent i;
        switch (target) {
            case "notifications":
                i = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName())
                        : appDetails(pkg);
                break;
            case "exactAlarm":
                i = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
                        : appDetails(pkg);
                break;
            case "fullScreen":
                i = Build.VERSION.SDK_INT >= 34
                        ? new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
                        : appDetails(pkg);
                break;
            case "battery":
                i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg);
                break;
            case "autostart":
                i = autostartIntent(ctx);
                break;
            case "lockScreen": // Xiaomi's separate "Show on lock screen" permission
                i = xiaomiPermissionsIntent(ctx);
                break;
            default:
                i = appDetails(pkg);
        }
        if (i == null) {
            i = appDetails(pkg);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            try {
                ctx.startActivity(appDetails(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (RuntimeException e2) {
                return false;
            }
        }
    }

    private static Intent appDetails(Uri pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg);
    }

    private static Intent autostartIntent(Context ctx) {
        for (String[] c : AUTOSTART) {
            Intent i = new Intent().setComponent(new ComponentName(c[0], c[1]));
            if (exists(ctx, i)) {
                return i;
            }
        }
        return null;
    }

    /**
     * Whether the screen really exists on this phone. (Intent.resolveActivity() does not check an explicit
     * component: it just returns it.) The maker packages are listed in the manifest's <queries>, or Android 11+
     * would hide them and this would always be false.
     */
    private static boolean exists(Context ctx, Intent i) {
        try {
            ctx.getPackageManager().getActivityInfo(i.getComponent(), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isXiaomi() {
        String m = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    private static Intent xiaomiPermissionsIntent(Context ctx) {
        Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", ctx.getPackageName());
        return exists(ctx, i) ? i : null;
    }
}
