package com.tvhome.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Per-TV setup from set-launcher.sh. Protected by the DUMP permission, which ADB's shell
 * has and ordinary apps can't get, so only the scripts can change the dock or key remaps.
 *   am broadcast -a com.tvhome.launcher.CONFIGURE -n com.tvhome.launcher/.ConfigReceiver
 *     --es dock "pkg1,pkg2"  --es keymap "247=app:pkg,176=panel"  --es hidden "pkg3,pkg4"
 *     --ez keep_adb_wifi true  --ez debug_keys true
 */
public class ConfigReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent i) {
        SharedPreferences.Editor e = ctx.getSharedPreferences("home", Context.MODE_PRIVATE).edit();
        if (i.hasExtra("dock")) {
            String dock = i.getStringExtra("dock");
            if (TextUtils.isEmpty(dock)) e.remove("dock").remove("dock_defaults");
            else e.putString("dock", dock).putString("dock_defaults",
                    TextUtils.join(",", ctx.getResources().getStringArray(R.array.default_dock)));
        }
        if (i.hasExtra("keymap")) e.putString("keymap", i.getStringExtra("keymap"));
        if (i.hasExtra("hidden")) e.putString("hidden", i.getStringExtra("hidden"));
        if (i.hasExtra("keep_adb_wifi")) e.putBoolean("keep_adb_wifi", i.getBooleanExtra("keep_adb_wifi", false));
        if (i.hasExtra("debug_keys")) e.putBoolean("debug_keys", i.getBooleanExtra("debug_keys", false));
        e.apply();
        setResultData("configured");
    }
}
