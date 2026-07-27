package org.schabi.newpipe.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

public final class AppIconHelper {
    public static final String ICON_DEFAULT = "default";
    public static final String ICON_ALT_1 = "alt1";
    public static final String ICON_ALT_2 = "alt2";

    private static final String[] VALUES = {ICON_DEFAULT, ICON_ALT_1, ICON_ALT_2};
    private static final String[] ALIASES = {
            "org.schabi.newpipe.MainActivityDefaultIconAlias",
            "org.schabi.newpipe.MainActivityIconAltOneAlias",
            "org.schabi.newpipe.MainActivityIconAltTwoAlias"
    };

    private AppIconHelper() {
    }

    public static boolean applyIcon(@NonNull final Context context, @NonNull final String value) {
        final int selectedIndex = indexOf(value);
        if (selectedIndex < 0) {
            return false;
        }
        final PackageManager packageManager = context.getPackageManager();
        for (int i = 0; i < ALIASES.length; i++) {
            final ComponentName component = new ComponentName(context, ALIASES[i]);
            final int state = i == selectedIndex
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            packageManager.setComponentEnabledSetting(component, state,
                    PackageManager.DONT_KILL_APP);
        }
        return true;
    }

    public static String sanitizeValue(final String value) {
        return indexOf(value) >= 0 ? value : ICON_DEFAULT;
    }

    private static int indexOf(final String value) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
